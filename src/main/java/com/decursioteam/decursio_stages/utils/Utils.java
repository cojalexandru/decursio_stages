package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

public class Utils {

    public static final Component DROPPED_ITEMS_MSG = Component.translatable( "decursio_stages.items.dropped").withStyle(ChatFormatting.RED);
    public static final Component ITEM_INTERACT_ERROR = Component.translatable( "decursio_stages.item.interact.error").withStyle(ChatFormatting.RED);
    public static final Component ITEM_PICKUP_ERROR = Component.translatable( "decursio_stages.item.pickup.error").withStyle(ChatFormatting.RED);
    public static final Component BLOCK_INTERACT_ERROR = Component.translatable( "decursio_stages.block.interact.error").withStyle(ChatFormatting.RED);
    public static final Component BLOCK_INTERACT_WARN = Component.translatable( "decursio_stages.block.interact.warn").withStyle(ChatFormatting.RED);
    public static final Component BLOCK_DESTROY_ERROR = Component.translatable( "decursio_stages.block.destroy").withStyle(ChatFormatting.RED);

    public static void dropItemStack(Player player, ItemStack itemStack)
    {
        player.displayClientMessage(DROPPED_ITEMS_MSG, true);
        Containers.dropItemStack(player.getCommandSenderWorld(), player.getX(), player.getY(), player.getZ(), itemStack);
    }

    public static void dropItemStack(EntityItemPickupEvent event, String s, Player player, boolean msg)
    {
        event.setCanceled(true);
        event.getItem().setPickUpDelay(RestrictionsData.getRestrictionData(s).getSettingsCodec().getPickupDelay());
        if(msg) player.displayClientMessage(ITEM_PICKUP_ERROR, true);
    }

}
