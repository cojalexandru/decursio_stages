package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({StonecutterMenu.class, ItemCombinerMenu.class})
public abstract class WorkstationQuickMoveMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void decstages$refresh(Player player, int slot, CallbackInfoReturnable<ItemStack> ci) {
        if (player instanceof ServerPlayer serverPlayer && (Object) this instanceof WorkstationRefresh)
            RecipeStages.refreshMenu((AbstractContainerMenu) (Object) this, serverPlayer);
    }
}
