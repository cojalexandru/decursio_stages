package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.orestaging.OreStages;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** Apply the requesting player's disguise before TOP's normal, extended and debug providers run. */
@Pseudo
@Mixin(targets = "mcjty.theoneprobe.network.PacketGetInfo", remap = false)
public abstract class OreProbeMixin {
    @Unique
    private static final ThreadLocal<ServerPlayer> DECSTAGES$PLAYER = new ThreadLocal<>();

    @Shadow private ResourceKey<Level> dim;
    @Shadow private BlockPos pos;
    @Shadow private ItemStack pickBlock;

    @Inject(method = "lambda$handle$0", at = @At("HEAD"))
    private void decstages$before(Supplier<?> ctx, CallbackInfo ci) {
        if (ctx.get() instanceof NetworkEvent.Context netCtx && netCtx.getSender() != null) {
            ServerPlayer player = netCtx.getSender();
            DECSTAGES$PLAYER.set(player);
            ServerLevel world = player.server.getLevel(dim);
            if (world != null && world.hasChunkAt(pos)) {
                BlockState real = world.getBlockState(pos);
                BlockState visible = OreStages.miningState(player, real);
                // The request can contain an old pick result immediately after a stage transition.
                if (visible != real) this.pickBlock = new ItemStack(visible.getBlock());
            }
        }
    }

    @Inject(method = "lambda$handle$0", at = @At("RETURN"))
    private void decstages$after(Supplier<?> ctx, CallbackInfo ci) {
        DECSTAGES$PLAYER.remove();
    }

    @ModifyVariable(method = "getProbeInfo", at = @At("STORE"), ordinal = 0)
    private static BlockState decstages$visibleState(BlockState state) {
        ServerPlayer player = DECSTAGES$PLAYER.get();
        return player != null ? OreStages.miningState(player, state) : state;
    }
}
