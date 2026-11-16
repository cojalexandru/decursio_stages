package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ItemEntity.class)
public abstract class ProcessorDropsMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void decstages$pickup(Player player,CallbackInfo ci) {
        if(player instanceof ServerPlayer serverPlayer && !ProcessorRecipes.mayCollect(serverPlayer,((ItemEntity)(Object)this).getPersistentData().getStringOr(ProcessorRecipes.RECEIPTS,""))) ci.cancel();
    }
    @Inject(method="tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V",at=@At("HEAD"),cancellable=true)
    private void decstages$merge(ItemEntity other,CallbackInfo ci) {
        String own=((ItemEntity)(Object)this).getPersistentData().getStringOr(ProcessorRecipes.RECEIPTS,"");
        if(!own.equals(other.getPersistentData().getStringOr(ProcessorRecipes.RECEIPTS,""))) ci.cancel();
    }
}
