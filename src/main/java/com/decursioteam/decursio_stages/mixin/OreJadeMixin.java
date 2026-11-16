package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.orestaging.OreStages;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Jade must select server data providers using the block visible to the requesting player. */
@Pseudo
@Mixin(targets = "snownee.jade.impl.BlockAccessorImpl$SyncData", remap = false)
public abstract class OreJadeMixin {
    @ModifyArg(
            method = "unpack",
            at = @At(value = "INVOKE", target = "Lsnownee/jade/impl/BlockAccessorImpl$Builder;blockState(Lnet/minecraft/world/level/block/state/BlockState;)Lsnownee/jade/impl/BlockAccessorImpl$Builder;"),
            require = 0
    )
    private BlockState decstages$visibleState(BlockState state, @Local(argsOnly = true) ServerPlayer player) {
        return OreStages.miningState(player, state);
    }
}
