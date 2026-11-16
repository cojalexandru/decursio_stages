package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.utils.CompiledRestriction;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.decursioteam.decursio_stages.utils.StageUtil;
import com.decursioteam.decursio_stages.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Locale;

import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;
import static com.decursioteam.decursio_stages.utils.Utils.ITEM_INTERACT_ERROR;
import static com.decursioteam.decursio_stages.utils.Utils.dropItemStack;

public class ItemEvents {
    @SubscribeEvent
    public void onItemPickupEvent(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack item = event.getItem().getItem();
        String containerName = player.containerMenu.getClass().getName();
        boolean inCustomContainer = player.containerMenu != player.inventoryMenu;
        var registry = RestrictionsData.getRegistry();

        registry.getRestrictions().forEach((name, restriction) -> {
            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            if (hasStage(player, stage)) return;

            var settings = restriction.getSettingsCodec();
            CompiledRestriction compiled = registry.compiled(name);

            if (inCustomContainer
                    && settings.getContainerListWhitelist() != compiled.containers.contains(containerName)
                    && compiled.matches(item, ResourceUtil.CHECK_TYPES.ALL)) {
                dropItemStack(event, name, player, false);
            }
            if (!settings.getCanPickup() && compiled.matches(item, ResourceUtil.CHECK_TYPES.ALL)) {
                dropItemStack(event, name, player, true);
            }
        });
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        final IStagesData stageData = StageUtil.getPlayerData(player);
        if (stageData == null) return;

        final ArrayList<String> playerStages = stageData.getStages();
        var registry = RestrictionsData.getRegistry();

        registry.getRestrictions().forEach((name, restriction) -> {
            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            var settings = restriction.getSettingsCodec();
            String tooltipMode = settings.getAdvancedTooltips();
            if ("NONE".equals(tooltipMode) || playerStages.contains(stage)) return;
            if (!"ALWAYS".equals(tooltipMode) && !("ADVANCED".equals(tooltipMode) && event.getFlags().isAdvanced())) return;
            if (!registry.compiled(name).matches(event.getItemStack(), ResourceUtil.CHECK_TYPES.ALL)) return;

            if (!settings.getItemTitle().isEmpty()) {
                event.getToolTip().set(0, Component.literal(settings.getItemTitle()));
            }
            event.getToolTip().add(Component.translatable("decursio_stages.tooltip.stage.message").withStyle(ChatFormatting.DARK_PURPLE).withStyle(ChatFormatting.BOLD)
                    .append(Component.translatable("decursio_stages.tooltip.stage.left_bracket").withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.translatable("decursio_stages.tooltip.stage.stage", stage).withStyle(ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD))
                    .append(Component.translatable("decursio_stages.tooltip.stage.right_bracket").withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.DARK_PURPLE))
            );
            if (!settings.getCanPickup()) {
                event.getToolTip().add(Component.translatable("decursio_stages.tooltip.pickup").withStyle(ChatFormatting.WHITE));
            }
            if (settings.getCheckPlayerInventory()) {
                event.getToolTip().add(Component.translatable("decursio_stages.tooltip.playerinventory").withStyle(ChatFormatting.WHITE));
            }
            if (settings.getCheckPlayerEquipment()) {
                event.getToolTip().add(Component.translatable("decursio_stages.tooltip.playerequipment").withStyle(ChatFormatting.WHITE));
            }
            if (!settings.getUsableItems()) {
                event.getToolTip().add(Component.translatable("decursio_stages.tooltip.usableitems").withStyle(ChatFormatting.WHITE));
            }
            if (!settings.getUsableBlocks()) {
                event.getToolTip().add(Component.translatable("decursio_stages.tooltip.usableblocks").withStyle(ChatFormatting.WHITE));
            }
            if (settings.getHideInJEI()) {
                event.getToolTip().add(Component.translatable("decursio_stages.tooltip.hideinjei").withStyle(ChatFormatting.WHITE));
            }
        });
    }

    @SubscribeEvent
    public void onPlayerInteractWithItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player == null) return;

        var registry = RestrictionsData.getRegistry();
        registry.getRestrictions().forEach((name, restriction) -> {
            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            if (!restriction.getSettingsCodec().getUsableItems() && !hasStage(player, stage)
                    && registry.compiled(name).matches(event.getItemStack(), ResourceUtil.CHECK_TYPES.ALL)) {
                event.setCanceled(true);
                player.displayClientMessage(Utils.ITEM_INTERACT_ERROR, true);
            }
        });
    }

    @SubscribeEvent
    public void onPlayerAttack(LivingAttackEvent event) {
        if (event.getSource() != null && event.getSource().getEntity() instanceof Player player
                && !player.level().isClientSide && !(player instanceof FakePlayer)) {
            var registry = RestrictionsData.getRegistry();
            registry.getRestrictions().forEach((name, restriction) -> {
                String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
                if (!restriction.getSettingsCodec().getUsableItems() && !hasStage(player, stage)
                        && registry.compiled(name).matches(player.getMainHandItem(), ResourceUtil.CHECK_TYPES.ALL)) {
                    event.setCanceled(true);
                    player.displayClientMessage(ITEM_INTERACT_ERROR, true);
                }
            });
        }
    }
}
