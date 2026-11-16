package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({InventoryMenu.class, CraftingMenu.class})
public abstract class RecipeQuickMoveMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void decstages$refresh(Player player, int slot, CallbackInfoReturnable<ItemStack> ci) {
        if (slot == 0 && player instanceof ServerPlayer) {
            RecipeStages.refresh((RecipeBookMenu<?>) (Object) this);
        }
    }
}
