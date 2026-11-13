package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.Locale;

import static com.decursioteam.decursio_stages.utils.ResourceUtil.*;
import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;
import static com.decursioteam.decursio_stages.utils.Utils.dropItemStack;

public class ContainerEvents {
    private final Map<UUID, InventorySnapshot> inventories = new HashMap<>();

    @SubscribeEvent
    public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        inventories.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { inventories.clear(); }

    private record InventorySnapshot(List<ItemStack> items, List<ItemStack> crafting, List<String> active, long revision) {
        static List<ItemStack> copy(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }
        static boolean matches(List<ItemStack> before, List<ItemStack> now) {
            if (before.size() != now.size()) return false;
            for (int i = 0; i < before.size(); i++) if (!ItemStack.matches(before.get(i), now.get(i))) return false;
            return true;
        }
    }

    @SubscribeEvent
    public void playerContainerOpenEvent(PlayerContainerEvent.Open event) {
        String containerName = event.getContainer().getClass().getName();
        if (Boolean.TRUE.equals(CommonConfig.debugMode.get()))
            event.getEntity().sendSystemMessage(Component.literal(containerName));

        if (event.getEntity() instanceof ServerPlayer player) {
            Registry.getRestrictions().forEach(restriction-> {
                if(!getApplyToFakePlayer(restriction) && player instanceof FakePlayer) return;

                String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage().toLowerCase(Locale.ROOT);
                if (!hasStage(player, stage)) {

                    if (!RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getContainerListWhitelist() && getContainers(restriction).contains(containerName)) {
                        for (ItemStack item : event.getEntity().inventoryMenu.getItems()) {
                            if (check(restriction, item, ResourceUtil.CHECK_TYPES.ALL)) {
                                dropItemStack(player, item);
                            }
                        }
                    }
                    if (RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getContainerListWhitelist() && !getContainers(restriction).contains(containerName)) {
                        for (ItemStack item : event.getEntity().inventoryMenu.getItems()) {
                            if (check(restriction, item, ResourceUtil.CHECK_TYPES.ALL)) {
                                dropItemStack(player, item);
                            }
                        }
                    }
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void clientInventoryTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        Player player = event.getEntity();

        var registry = RestrictionsData.getRegistry();
        // Check stage policy each tick, including dynamic KubeJS Check handlers.
        List<String> active = registry.getRestrictions().entrySet().stream()
                .filter(entry -> (!(player instanceof FakePlayer) || entry.getValue().getSettingsCodec().getApplyToFakePlayers())
                        && !hasStage(player, entry.getValue().getData().getStage().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getKey).toList();
        var items = player.getInventory().getNonEquipmentItems();
        var crafting = player.inventoryMenu.getCraftSlots();
        List<ItemStack> craftingItems = new java.util.ArrayList<>();
        for (int i = 0; i < crafting.getContainerSize(); i++) craftingItems.add(crafting.getItem(i));
        var previous = inventories.get(player.getUUID());
        if (previous != null && previous.revision == registry.revision() && previous.active.equals(active)
                && InventorySnapshot.matches(previous.items, items) && InventorySnapshot.matches(previous.crafting, craftingItems)) return;

        for (String restriction : active) {
            var settings = RestrictionsData.getRestrictionData(restriction).getSettingsCodec();
            boolean listed = getContainers(restriction).contains("decursio_stages.inventoryMenu.CraftingGrid");
            if (settings.getContainerListWhitelist() != listed) {
                boolean changed = false;
                for (int i = 0; i < crafting.getContainerSize(); i++) {
                    if (check(restriction, crafting.getItem(i), ResourceUtil.CHECK_TYPES.ALL)) {
                        dropItemStack(player, crafting.getItem(i));
                        changed = true;
                    }
                }
                if (changed) player.inventoryMenu.slotsChanged(player.getInventory());
            }
            if (settings.getCheckPlayerInventory()) {
                for (ItemStack item : items) {
                    if (check(restriction, item, ResourceUtil.CHECK_TYPES.ALL)) dropItemStack(player, item);
                }
            }
        }
        craftingItems.clear();
        for (int i = 0; i < crafting.getContainerSize(); i++) craftingItems.add(crafting.getItem(i));
        inventories.put(player.getUUID(), new InventorySnapshot(InventorySnapshot.copy(items),
                InventorySnapshot.copy(craftingItems), active, registry.revision()));
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof Player player) {
            Registry.getRestrictions().forEach(restriction -> {
                if(!getApplyToFakePlayer(restriction) && player instanceof FakePlayer) return;

                String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage().toLowerCase(Locale.ROOT);
                if (RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getCheckPlayerEquipment() && !hasStage(player, stage)) {
                    for (ItemStack item : java.util.List.of(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD), player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST), player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS), player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET))) {
                        if (check(restriction, item, ResourceUtil.CHECK_TYPES.ALL)) {
                            dropItemStack(player, item);
                        }
                    }
                }
            });
        }
    }
}
