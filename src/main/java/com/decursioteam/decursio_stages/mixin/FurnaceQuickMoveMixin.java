package com.decursioteam.decursio_stages.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceMenu.class)
public abstract class FurnaceQuickMoveMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void decstages$take(Player player, int slot, CallbackInfoReturnable<ItemStack> ci) {
        if (slot == 2 && !((AbstractFurnaceMenu) (Object) this).getSlot(2).mayPickup(player)) {
            ci.setReturnValue(ItemStack.EMPTY);
        }
    }
}
