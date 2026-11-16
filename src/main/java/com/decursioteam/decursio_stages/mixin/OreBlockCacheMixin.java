package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.orestaging.OreViewCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class OreBlockCacheMixin {
    @Inject(method = "setBlockState", at = {@At("HEAD"), @At("RETURN")})
    private void decstages$changed(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel() instanceof ServerLevel level) {
            OreViewCache.changed(level, chunk.getPos());
        }
    }
}
