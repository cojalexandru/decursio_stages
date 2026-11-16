package com.decursioteam.decursio_stages.orestaging;

import com.decursioteam.decursio_stages.mixin.ChunkPacketDataAccessor;
import com.decursioteam.decursio_stages.mixin.SectionUpdateAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;

public final class OrePackets {
    private OrePackets() {}

    public static Packet<?> rewrite(ServerPlayer player, Packet<?> packet) {
        if (packet instanceof ClientboundRecipePacket book)
            return com.decursioteam.decursio_stages.recipes.RecipeVisibilitySync.filter(player, book);
        // Avoid stage checks for chat, inventory, movement, and other unrelated traffic.
        if (!(packet instanceof ClientboundLevelChunkWithLightPacket || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundSectionBlocksUpdatePacket || packet instanceof ClientboundForgetLevelChunkPacket
                || packet instanceof ClientboundBundlePacket || packet instanceof ClientboundLevelEventPacket
                || packet instanceof ClientboundLightUpdatePacket)) return packet;
        if (packet instanceof ClientboundBundlePacket bundle) {
            var children = new ArrayList<Packet<? super ClientGamePacketListener>>();
            boolean changed = false;
            for (var child : bundle.subPackets()) {
                var rewritten = rewrite(player, child);
                changed |= rewritten != child;
                children.add((Packet<? super ClientGamePacketListener>) rewritten);
            }
            return changed ? new ClientboundBundlePacket(children) : packet;
        }
        var view = OreStages.view(player);
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
            var state = player.serverLevel().hasChunkAt(block.getPos()) ? player.serverLevel().getBlockState(block.getPos()) : block.getBlockState();
            var replacement = view.replace(state);
            return replacement == block.getBlockState() ? packet : new ClientboundBlockUpdatePacket(block.getPos(), replacement);
        }
        if (view.empty()) return packet;
        if (packet instanceof ClientboundLightUpdatePacket light) {
            var correction = OreLighting.correction(light.getX(), light.getZ(), view);
            if (!correction.changed()) return packet;
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundLightUpdatePacket.STREAM_CODEC.encode(buffer, light);
                var copy = ClientboundLightUpdatePacket.STREAM_CODEC.decode(buffer);
                correction.apply(copy.getLightData());
                return copy;
            } finally { buffer.release(); }
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
            boolean changed = false;
            for (var state : ((SectionUpdateAccessor) section).decstages$states()) {
                if (view.replace(state) != state) { changed = true; break; }
            }
            if (!changed) return packet;
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
            var replacement = view.replace(state);
            return replacement == state ? packet : new ClientboundLevelEventPacket(event.getType(), event.getPos(), Block.getId(replacement), event.isGlobalEvent());
        }
        return packet;
    }

    private static Packet<?> chunk(ServerPlayer player, ClientboundLevelChunkWithLightPacket packet, OreStages.View view) {
        var cache = OreViewCache.state(player.serverLevel());
        var pos = new ChunkPos(packet.getX(), packet.getZ());
        var key = new OreViewCache.PacketKey(view.profile, packet);
        long stamp = cache.stamp(pos);
        var cached = cache.packet(key, stamp);
        if (cached != null) return cached;
        byte[] transformed = transformSections(player, packet, view);
        var correction = OreLighting.correction(packet.getX(), packet.getZ(), view);
        if (transformed == null && !correction.changed()) {
            cache.packet(key, pos, stamp, packet, packetBytes(packet));
            return packet;
        }
        // Copy only packets that change, before touching any arrays shared by a broadcast.
        RegistryFriendlyByteBuf serialized = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(serialized, packet);
            var copy = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(serialized);
            if (transformed != null) ((ChunkPacketDataAccessor) copy.getChunkData()).decstages$buffer(transformed);
            correction.apply(copy.getLightData());
            cache.packet(key, pos, stamp, copy, packetBytes(packet) + packetBytes(copy));
            return copy;
        } finally { serialized.release(); }
    }

    private static long packetBytes(ClientboundLevelChunkWithLightPacket packet) {
        // Include an allowance for light/entity metadata as well as the exact section payload.
        return ((ChunkPacketDataAccessor) packet.getChunkData()).decstages$buffer().length + 128L * 1024;
    }

    private static byte[] transformSections(ServerPlayer player, ClientboundLevelChunkWithLightPacket packet, OreStages.View view) {
        FriendlyByteBuf input = packet.getChunkData().getReadBuffer();
        FriendlyByteBuf output = null;
        try {
            var biomes = player.registryAccess().registryOrThrow(Registries.BIOME);
            while (input.isReadable()) {
                int start = input.readerIndex();
                var section = new LevelChunkSection(biomes);
                section.read(input);
                int end = input.readerIndex();
                if (section.maybeHas(state -> view.replace(state) != state)) {
                    if (output == null) {
                        output = new FriendlyByteBuf(Unpooled.buffer());
                        output.writeBytes(input, 0, start);
                    }
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        var state = section.getBlockState(x, y, z);
                        var replacement = view.replace(state);
                        if (state != replacement) section.setBlockState(x, y, z, replacement, false);
                    }
                    section.write(output);
                } else if (output != null) output.writeBytes(input, start, end - start);
            }
            if (output == null) return null;
            byte[] bytes = new byte[output.readableBytes()];
            output.readBytes(bytes);
            return bytes;
        } finally { input.release(); if (output != null) output.release(); }
    }
}
