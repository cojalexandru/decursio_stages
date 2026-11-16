package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.utils.CompiledRestriction;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;
import static com.decursioteam.decursio_stages.utils.Utils.dropItemStack;

public class ContainerEvents {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private final Map<UUID, InventorySnapshot> inventories = new HashMap<>();

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        inventories.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        inventories.clear();
    }

    private record InventorySnapshot(List<ItemStack> items, List<ItemStack> crafting, List<String> active, long revision) {
        static List<ItemStack> copy(List<ItemStack> stacks) {
            return stacks.stream().map(ItemStack::copy).toList();
        }

        static boolean matches(List<ItemStack> before, List<ItemStack> now) {
            if (before.size() != now.size()) return false;
            for (int i = 0; i < before.size(); i++) {
                if (!ItemStack.matches(before.get(i), now.get(i))) return false;
            }
            return true;
        }
    }

    @SubscribeEvent
    public void playerContainerOpenEvent(PlayerContainerEvent.Open event) {
        String containerName = event.getContainer().getClass().getName();
        if (Boolean.TRUE.equals(CommonConfig.debugMode.get())) {
            event.getEntity().sendSystemMessage(Component.literal(containerName));
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var registry = RestrictionsData.getRegistry();
        registry.getRestrictions().forEach((name, restriction) -> {
            if (!restriction.getSettingsCodec().getApplyToFakePlayers() && player instanceof FakePlayer) return;

            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            if (hasStage(player, stage)) return;

            CompiledRestriction compiled = registry.compiled(name);
            if (restriction.getSettingsCodec().getContainerListWhitelist() != compiled.containers.contains(containerName)) {
                for (ItemStack item : player.inventoryMenu.getItems()) {
                    if (compiled.matches(item, ResourceUtil.CHECK_TYPES.ALL)) {
                        dropItemStack(player, item);
                    }
                }
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void clientInventoryTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        Player player = event.getEntity();

        var registry = RestrictionsData.getRegistry();
        // Check stage policy each tick, including dynamic KubeJS Check handlers.
        List<String> active = registry.getRestrictions().entrySet().stream()
                .filter(entry -> (!(player instanceof FakePlayer) || entry.getValue().getSettingsCodec().getApplyToFakePlayers())
                        && !hasStage(player, entry.getValue().getData().getStage().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getKey).toList();
        var items = player.getInventory().items;
        var crafting = player.inventoryMenu.getCraftSlots();
        List<ItemStack> craftingItems = new ArrayList<>();
        for (int i = 0; i < crafting.getContainerSize(); i++) craftingItems.add(crafting.getItem(i));
        var previous = inventories.get(player.getUUID());
        if (previous != null && previous.revision == registry.revision() && previous.active.equals(active)
                && InventorySnapshot.matches(previous.items, items) && InventorySnapshot.matches(previous.crafting, craftingItems)) return;

        for (String name : active) {
            var settings = registry.getRestrictions().get(name).getSettingsCodec();
            CompiledRestriction compiled = registry.compiled(name);
            boolean listed = compiled.containers.contains("decursio_stages.inventoryMenu.CraftingGrid");
            if (settings.getContainerListWhitelist() != listed) {
                boolean changed = false;
                for (int i = 0; i < crafting.getContainerSize(); i++) {
                    if (compiled.matches(crafting.getItem(i), ResourceUtil.CHECK_TYPES.ALL)) {
                        dropItemStack(player, crafting.getItem(i));
                        changed = true;
                    }
                }
                if (changed) player.inventoryMenu.slotsChanged(player.getInventory());
            }
            if (settings.getCheckPlayerInventory()) {
                for (ItemStack item : items) {
                    if (compiled.matches(item, ResourceUtil.CHECK_TYPES.ALL)) dropItemStack(player, item);
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
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Player player)) return;

        var registry = RestrictionsData.getRegistry();
        registry.getRestrictions().forEach((name, restriction) -> {
            var settings = restriction.getSettingsCodec();
            if (!settings.getApplyToFakePlayers() && player instanceof FakePlayer) return;

            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            if (settings.getCheckPlayerEquipment() && !hasStage(player, stage)) {
                CompiledRestriction compiled = registry.compiled(name);
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    ItemStack item = player.getItemBySlot(slot);
                    if (compiled.matches(item, ResourceUtil.CHECK_TYPES.ALL)) {
                        dropItemStack(player, item);
                    }
                }
            }
        });
    }
}
