package com.decursioteam.decursio_stages.orestaging;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Bounded, dimension-local caches. Player identity is deliberately not part of a cache key. */
@Mod.EventBusSubscriber(modid = "decursio_stages")
public final class OreViewCache {
    public static final class Profile {
        private final Map<BlockState, BlockState> states;
        private final int hash;
        Profile(Map<BlockState, BlockState> states) { this.states = states; hash = states.hashCode(); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) { return other instanceof Profile profile && states.equals(profile.states); }
    }
    record LightKey(Profile profile, ChunkPos pos) {}
    record PacketKey(Profile profile, ClientboundLevelChunkWithLightPacket packet) {}
    record Entry<T>(long stamp, T value, long bytes) {}
    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();
    static synchronized State state(ServerLevel level) { return LEVELS.computeIfAbsent(level, ignored -> new State()); }

    static final class State {
        private long clock;
        private final Map<ChunkPos, Long> versions = new HashMap<>();
        private final LinkedHashMap<LightKey, Entry<OreLighting.BlockLight>> lights = new LinkedHashMap<>(16, .75f, true);
        private final LinkedHashMap<PacketKey, Entry<Packet<?>>> packets = new LinkedHashMap<>(16, .75f, true);
        private long lightBytes, packetBytes;

        synchronized long stamp(ChunkPos pos) {
            long latest = 0;
            for (int x = pos.x - 1; x <= pos.x + 1; x++) for (int z = pos.z - 1; z <= pos.z + 1; z++)
                latest = Math.max(latest, versions.getOrDefault(new ChunkPos(x, z), 0L));
            return latest;
        }
        synchronized void invalidate(ChunkPos pos) {
            // The monotonically increasing clock makes max(neighbor versions) change on every edit.
            if (versions.size() >= 8192) { versions.clear(); clearResults(); }
            versions.put(pos, ++clock);
        }
        synchronized OreLighting.BlockLight light(LightKey key, long stamp) {
            if (stamp(key.pos) != stamp) return null;
            var entry = lights.get(key);
            return entry != null && entry.stamp == stamp ? entry.value : null;
        }
        synchronized void light(LightKey key, long stamp, OreLighting.BlockLight value) {
            if (stamp(key.pos) != stamp) return; // Do not retain a result built across a concurrent edit.
            var old = lights.put(key, new Entry<>(stamp, value, value.bytes()));
            if (old != null) lightBytes -= old.bytes;
            lightBytes += value.bytes();
            while (lights.size() > 128 || lightBytes > 8L * 1024 * 1024) {
                var first = lights.entrySet().iterator(); lightBytes -= first.next().getValue().bytes; first.remove();
            }
        }
        synchronized Packet<?> packet(PacketKey key, long stamp) {
            if (stamp(new ChunkPos(key.packet.getX(), key.packet.getZ())) != stamp) return null;
            var entry = packets.get(key);
            return entry != null && entry.stamp == stamp ? entry.value : null;
        }
        synchronized void packet(PacketKey key, ChunkPos pos, long stamp, Packet<?> value, long bytes) {
            if (stamp(pos) != stamp || bytes > 8L * 1024 * 1024) return;
            var old = packets.put(key, new Entry<>(stamp, value, bytes));
            if (old != null) packetBytes -= old.bytes;
            packetBytes += bytes;
            while (packets.size() > 64 || packetBytes > 8L * 1024 * 1024) {
                var first = packets.entrySet().iterator(); packetBytes -= first.next().getValue().bytes; first.remove();
            }
        }
        synchronized void clearResults() { lights.clear(); packets.clear(); lightBytes = 0; packetBytes = 0; }
        synchronized void unloaded(ChunkPos pos) {
            invalidate(pos);
            // Drop this region's packet/entity data without discarding unrelated players' regions.
            var lightEntries = lights.entrySet().iterator();
            while (lightEntries.hasNext()) {
                var entry = lightEntries.next();
                if (near(entry.getKey().pos, pos)) { lightBytes -= entry.getValue().bytes; lightEntries.remove(); }
            }
            var packetEntries = packets.entrySet().iterator();
            while (packetEntries.hasNext()) {
                var entry = packetEntries.next();
                var packet = entry.getKey().packet;
                if (near(new ChunkPos(packet.getX(), packet.getZ()), pos)) {
                    packetBytes -= entry.getValue().bytes; packetEntries.remove();
                }
            }
        }
        private static boolean near(ChunkPos a, ChunkPos b) { return Math.abs(a.x - b.x) <= 1 && Math.abs(a.z - b.z) <= 1; }
    }

    private static synchronized State existing(ServerLevel level) { return LEVELS.get(level); }
    public static void changed(ServerLevel level, ChunkPos pos) {
        var cache = existing(level);
        if (cache != null) cache.invalidate(pos);
    }
    public static synchronized void clear() { LEVELS.clear(); }
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) changed(level, event.getChunk().getPos());
    }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var cache = existing(level);
            if (cache != null) cache.unloaded(event.getChunk().getPos());
        }
    }
    @SubscribeEvent public static void levelUnloaded(LevelEvent.Unload event) {
        synchronized (OreViewCache.class) { LEVELS.remove(event.getLevel()); }
    }
    private OreViewCache() {}
}
