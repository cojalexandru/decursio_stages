package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.orestaging.OreStages;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Apply the requesting player's disguise before TOP's normal, extended and debug providers run. */
@Pseudo
@Mixin(targets = "mcjty.theoneprobe.network.PacketGetInfo", remap = false)
public abstract class OreProbeMixin {
    @WrapOperation(method = "getProbeInfo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState decstages$visibleState(Level level, BlockPos pos, Operation<BlockState> original,
                                                    @Local(argsOnly = true) Player player) {
        BlockState real = original.call(level, pos);
        return player instanceof ServerPlayer serverPlayer ? OreStages.miningState(serverPlayer, real) : real;
    }

    @ModifyVariable(method = "getProbeInfo", at = @At("HEAD"), argsOnly = true)
    private static ItemStack decstages$visiblePick(ItemStack picked, @Local(argsOnly = true) Player player,
                                                 @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer) || !level.hasChunkAt(pos)) return picked;
        BlockState real = level.getBlockState(pos);
        BlockState visible = OreStages.miningState(serverPlayer, real);
        // The request can contain an old pick result immediately after a stage transition.
        return visible == real ? picked : new ItemStack(visible.getBlock());
    }
}
