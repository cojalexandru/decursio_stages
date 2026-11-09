package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.decursioteam.decursio_stages.utils.StageUtil;
import com.decursioteam.decursio_stages.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Locale;

import static com.decursioteam.decursio_stages.utils.ResourceUtil.check;
import static com.decursioteam.decursio_stages.utils.ResourceUtil.getContainers;
import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;
import static com.decursioteam.decursio_stages.utils.Utils.ITEM_INTERACT_ERROR;
import static com.decursioteam.decursio_stages.utils.Utils.dropItemStack;

public class ItemEvents {
    @SubscribeEvent
    public void onItemPickupEvent(EntityItemPickupEvent event) {
        if (event.getEntity() != null) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ItemStack item = event.getItem().getItem();
            Registry.getRestrictions().forEach((s) -> {                String stage = RestrictionsData.getRestrictionData(s).getData().getStage().toLowerCase(Locale.ROOT).toLowerCase(Locale.ROOT);
                if (!hasStage(player, stage) && player.containerMenu != player.inventoryMenu) {
                    if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getContainerListWhitelist()) {
                        if (getContainers(s).contains(player.containerMenu.getClass().getName())) {
                            if (check(s, item, ResourceUtil.CHECK_TYPES.ALL)) {
                                dropItemStack(event, s, player, false);
                            }
                        }
                    }
                    if (RestrictionsData.getRestrictionData(s).getSettingsCodec().getContainerListWhitelist()) {
                        if (!getContainers(s).contains(player.containerMenu.getClass().getName())) {
                            if (check(s, item, ResourceUtil.CHECK_TYPES.ALL)) {
                                dropItemStack(event, s, player, false);
                            }
                        }
                    }
                }
                if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getCanPickup() && !hasStage(player, stage)) {
                    if (check(s, item, ResourceUtil.CHECK_TYPES.ALL)) {
                        dropItemStack(event, s, player, true);
                    }
                }
            });
        }
    }
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onItemTooltip(ItemTooltipEvent event) {
        Player player = Minecraft.getInstance().player;
        final IStagesData stageData = StageUtil.getPlayerData(player);
        if (stageData == null) return;
        final ArrayList<String> playerStages = stageData.getStages();
        Registry.getRestrictions().forEach((s) -> {
            String stage = RestrictionsData.getRestrictionData(s).getData().getStage().toLowerCase(Locale.ROOT).toLowerCase(Locale.ROOT);
            if (RestrictionsData.getRestrictionData(s).getSettingsCodec().getAdvancedTooltips().equals("NONE")) return;
            if ((RestrictionsData.getRestrictionData(s).getSettingsCodec().getAdvancedTooltips().equals("ADVANCED") && !playerStages.contains(stage) && event.getFlags().isAdvanced()) || !playerStages.contains(stage) && RestrictionsData.getRestrictionData(s).getSettingsCodec().getAdvancedTooltips().equals("ALWAYS")) {
                ItemStack itemStack = null;
                //Collect ingredients from mod list
                if (check(s, event.getItemStack(), ResourceUtil.CHECK_TYPES.ALL)) {
                    itemStack = event.getItemStack();
                }

                if (itemStack != null) {
                    if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getItemTitle().isEmpty()) {
                        event.getToolTip().set(0, Component.literal(RestrictionsData.getRestrictionData(s).getSettingsCodec().getItemTitle()));
                    }
                    event.getToolTip().add(Component.translatable("decursio_stages.tooltip.stage.message").withStyle(ChatFormatting.DARK_PURPLE).withStyle(ChatFormatting.BOLD)
                            .append(Component.translatable("decursio_stages.tooltip.stage.left_bracket").withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.DARK_PURPLE))
                            .append(Component.translatable("decursio_stages.tooltip.stage.stage", stage).withStyle(ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD))
                            .append(Component.translatable("decursio_stages.tooltip.stage.right_bracket").withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.DARK_PURPLE))
                    );
                    if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getCanPickup()) {
                        event.getToolTip().add(Component.translatable("decursio_stages.tooltip.pickup").withStyle(ChatFormatting.WHITE));
                    }
                    if (RestrictionsData.getRestrictionData(s).getSettingsCodec().getCheckPlayerInventory()) {
                        event.getToolTip().add(Component.translatable("decursio_stages.tooltip.playerinventory").withStyle(ChatFormatting.WHITE));
                    }
                    if (RestrictionsData.getRestrictionData(s).getSettingsCodec().getCheckPlayerEquipment()) {
                        event.getToolTip().add(Component.translatable("decursio_stages.tooltip.playerequipment").withStyle(ChatFormatting.WHITE));
                    }
                    if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getUsableItems()) {
                        event.getToolTip().add(Component.translatable("decursio_stages.tooltip.usableitems").withStyle(ChatFormatting.WHITE));
                    }
                    if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getUsableBlocks()) {
                        event.getToolTip().add(Component.translatable("decursio_stages.tooltip.usableblocks").withStyle(ChatFormatting.WHITE));
                    }
                    if (RestrictionsData.getRestrictionData(s).getSettingsCodec().getHideInJEI()) {
                        event.getToolTip().add(Component.translatable("decursio_stages.tooltip.hideinjei").withStyle(ChatFormatting.WHITE));
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public void onPlayerInteractWithItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() != null) {
            Player player = event.getEntity();
            Registry.getRestrictions().forEach((s) -> {
                String stage = RestrictionsData.getRestrictionData(s).getData().getStage().toLowerCase(Locale.ROOT);

                if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getUsableItems() && !hasStage(player, stage)) {
                    if (check(s, event.getItemStack(), ResourceUtil.CHECK_TYPES.ALL)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Utils.ITEM_INTERACT_ERROR, true);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public void onPlayerAttack(LivingAttackEvent event) {
        if (event.getSource() != null && event.getSource().getEntity() instanceof Player player && event.isCancelable() && event.getSource().getEntity() != null && !event.getSource().getEntity().level().isClientSide && !(event.getSource().getEntity() instanceof FakePlayer)) {
            Registry.getRestrictions().forEach((s) -> {
                String stage = RestrictionsData.getRestrictionData(s).getData().getStage().toLowerCase(Locale.ROOT);

                if (!RestrictionsData.getRestrictionData(s).getSettingsCodec().getUsableItems() && !hasStage(player, stage)) {
                    if (check(s, player.getMainHandItem(), ResourceUtil.CHECK_TYPES.ALL)) {
                        event.setCanceled(true);
                        player.displayClientMessage(ITEM_INTERACT_ERROR, true);
                    }
                }
            });
        }
    }
}
