package com.decursioteam.decursio_stages.client.screens;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Client-only, inert menu. The offscreen empty slot lets EMI recognize this screen. */
public final class RestrictionEditorMenu extends AbstractContainerMenu {
    public RestrictionEditorMenu() {
        super(null, -1);
        addSlot(new Slot(new SimpleContainer(1), 0, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
}
