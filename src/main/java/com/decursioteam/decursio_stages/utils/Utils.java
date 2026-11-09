package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

public class Utils {

    public static final Component DROPPED_ITEMS_MSG = Component.translatable( "decursio_stages.items.dropped").withStyle(ChatFormatting.RED);
    public static final Component ITEM_INTERACT_ERROR = Component.translatable( "decursio_stages.item.interact.error").withStyle(ChatFormatting.RED);
    public static final Component ITEM_PICKUP_ERROR = Component.translatable( "decursio_stages.item.pickup.error").withStyle(ChatFormatting.RED);
    public static final Component BLOCK_INTERACT_ERROR = Component.translatable( "decursio_stages.block.interact.error").withStyle(ChatFormatting.RED);
    public static final Component BLOCK_INTERACT_WARN = Component.translatable( "decursio_stages.block.interact.warn").withStyle(ChatFormatting.RED);
    public static final Component BLOCK_DESTROY_ERROR = Component.translatable( "decursio_stages.block.destroy").withStyle(ChatFormatting.RED);

    public static void dropItemStack(Player player, ItemStack itemStack)
    {
        player.sendOverlayMessage(DROPPED_ITEMS_MSG);
        Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), itemStack);
    }

    public static void dropItemStack(ItemEntityPickupEvent.Pre event, String s, Player player, boolean msg)
    {
        event.setCanPickup(net.minecraft.util.TriState.FALSE);
        event.getItemEntity().setPickUpDelay(RestrictionsData.getRestrictionData(s).getSettingsCodec().getPickupDelay());
        if(msg) player.sendOverlayMessage(ITEM_PICKUP_ERROR);
    }

}
