package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.orestaging.OreStages;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Jade must select server data providers using the block visible to the requesting player. */
@Pseudo
@Mixin(targets = "snownee.jade.impl.BlockAccessorImpl$SyncData", remap = false)
public abstract class OreJadeMixin {
    @WrapOperation(method = "unpack", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState decstages$visibleState(ServerLevel level, BlockPos pos, Operation<BlockState> original,
                                             @Local(argsOnly = true) ServerPlayer player) {
        return OreStages.miningState(player, original.call(level, pos));
    }
}
