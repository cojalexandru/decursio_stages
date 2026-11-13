package com.decursioteam.decursio_stages.orestaging;

import com.decursioteam.decursio_stages.mixin.ChunkPacketDataAccessor;
import com.decursioteam.decursio_stages.mixin.SectionUpdateAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

import java.util.ArrayList;

public final class OrePackets {
    private OrePackets() {}

    public static Packet<?> rewrite(ServerPlayer player, Packet<?> packet) {
        // Avoid stage checks for chat, inventory, movement, and other unrelated traffic.
        if (!(packet instanceof ClientboundLevelChunkWithLightPacket || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundSectionBlocksUpdatePacket || packet instanceof ClientboundForgetLevelChunkPacket
                || packet instanceof ClientboundBundlePacket || packet instanceof ClientboundLevelEventPacket
                || packet instanceof ClientboundLightUpdatePacket)) return packet;
        var view = OreStages.view(player);
        if (packet instanceof ClientboundBundlePacket bundle) {
            var children = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (var child : bundle.subPackets()) children.add((Packet<? super ClientGamePacketListener>) rewrite(player, child));
            return new ClientboundBundlePacket(children);
        }
        if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            view.chunks.remove(forget.pos());
            return packet;
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            view.chunks.add(new ChunkPos(chunk.getX(), chunk.getZ()));
            if (view.empty()) return packet;
            return chunk(player, chunk, view);
        }
        if (packet instanceof ClientboundBlockUpdatePacket block) {
            // Mining can already have substituted the local state. Always map the real state once.
            var state = player.level().hasChunkAt(block.getPos()) ? player.level().getBlockState(block.getPos()) : block.getBlockState();
            var replacement = view.replace(state);
            return replacement == block.getBlockState() ? packet : new ClientboundBlockUpdatePacket(block.getPos(), replacement);
        }
        if (view.empty()) return packet;
        if (packet instanceof ClientboundLightUpdatePacket light) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundLightUpdatePacket.STREAM_CODEC.encode(buffer, light);
                var copy = ClientboundLightUpdatePacket.STREAM_CODEC.decode(buffer);
                OreLighting.rewrite(copy.getLightData(), copy.getX(), copy.getZ(), view);
                return copy;
            } finally { buffer.release(); }
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.encode(buffer, section);
                var copy = ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.decode(buffer);
                var states = ((SectionUpdateAccessor) copy).decstages$states();
                for (int i = 0; i < states.length; i++) states[i] = view.replace(states[i]);
                return copy;
            } finally { buffer.release(); }
        }
        if (packet instanceof ClientboundLevelEventPacket event && event.getType() == 2001) {
            var state = Block.stateById(event.getData());
            return new ClientboundLevelEventPacket(event.getType(), event.getPos(), Block.getId(view.replace(state)), event.isGlobalEvent());
        }
        return packet;
    }

    private static Packet<?> chunk(ServerPlayer player, ClientboundLevelChunkWithLightPacket packet, OreStages.View view) {
        FriendlyByteBuf input = packet.getChunkData().getReadBuffer();
        FriendlyByteBuf output = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var factory = PalettedContainerFactory.create(player.registryAccess());
            boolean changed = false;
            while (input.isReadable()) {
                var section = new LevelChunkSection(factory);
                section.read(input);
                if (section.maybeHas(state -> view.replace(state) != state)) {
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        var state = section.getBlockState(x, y, z);
                        var replacement = view.replace(state);
                        if (state != replacement) section.setBlockState(x, y, z, replacement, false);
                    }
                    changed = true;
                }
                section.write(output);
            }

            // Clone the complete packet before replacing its section bytes. Broadcast packets are shared.
            RegistryFriendlyByteBuf serialized = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            try {
                ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(serialized, packet);
                var copy = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(serialized);
                byte[] bytes = new byte[output.readableBytes()];
                output.readBytes(bytes);
                if (changed) ((ChunkPacketDataAccessor) copy.getChunkData()).decstages$buffer(bytes);
                OreLighting.rewrite(copy.getLightData(), copy.getX(), copy.getZ(), view);
                return copy;
            } finally { serialized.release(); }
        } finally { input.release(); output.release(); }
    }
}
