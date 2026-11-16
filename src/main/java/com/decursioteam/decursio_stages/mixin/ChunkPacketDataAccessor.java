package com.decursioteam.decursio_stages.mixin;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLevelChunkPacketData.class)
public interface ChunkPacketDataAccessor {
    @Accessor("buffer") byte[] decstages$buffer();
    @Mutable @Accessor("buffer") void decstages$buffer(byte[] buffer);
}
