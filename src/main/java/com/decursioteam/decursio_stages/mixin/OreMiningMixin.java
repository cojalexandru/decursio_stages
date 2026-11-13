package com.decursioteam.decursio_stages.mixin;
import com.decursioteam.decursio_stages.orestaging.OreStages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(ServerPlayerGameMode.class)
public abstract class OreMiningMixin {
    @Shadow @Final protected ServerPlayer player;
    @Redirect(method = {"tick", "handleBlockBreakAction", "destroyBlock"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState decstages$mining(ServerLevel level, BlockPos pos) {
        return OreStages.miningState(player, level.getBlockState(pos));
    }
    @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState decstages$interaction(Level level, BlockPos pos) {
        return OreStages.miningState(player, level.getBlockState(pos));
    }
}
