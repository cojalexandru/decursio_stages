package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class RecipeClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void decstages$refresh(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            RecipeStages.refreshMenu((AbstractContainerMenu) (Object) this, serverPlayer);
        }
    }
}
