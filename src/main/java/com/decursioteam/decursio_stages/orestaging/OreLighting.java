package com.decursioteam.decursio_stages.orestaging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Rebuild block light with vanilla propagation against the player's visible block states. */
public final class OreLighting {
    private OreLighting() {}

    static final class BlockLight {
        static final BlockLight UNCHANGED = new BlockLight(null, null, List.of());
        final BitSet mask, empty;
        final List<byte[]> layers;
        BlockLight(BitSet mask, BitSet empty, List<byte[]> layers) { this.mask = mask; this.empty = empty; this.layers = layers; }
        boolean changed() { return mask != null; }
        long bytes() { return 128L + layers.size() * 2048L; }
        void apply(ClientboundLightUpdatePacketData data) {
            if (!changed()) return;
            data.getBlockYMask().clear(); data.getBlockYMask().or(mask);
            data.getEmptyBlockYMask().clear(); data.getEmptyBlockYMask().or(empty);
            data.getBlockUpdates().clear();
            for (byte[] layer : layers) data.getBlockUpdates().add(layer.clone());
        }
    }

    public static void rewrite(ClientboundLightUpdatePacketData data, int x, int z, OreStages.View view) {
        correction(x, z, view).apply(data);
    }

    static BlockLight correction(int x, int z, OreStages.View view) {
        var cache = OreViewCache.state(view.level);
        var pos = new ChunkPos(x, z);
        var key = new OreViewCache.LightKey(view.profile, pos);
        long stamp = cache.stamp(pos);
        BlockLight cached = cache.light(key, stamp);
        if (cached != null) return cached;
        BlockLight result = build(x, z, view);
        cache.light(key, stamp, result);
        return result;
    }

    // Block light travels at most 14 blocks from a level-15 emitter. A one-chunk border
    // therefore contains every source/path that can affect the central chunk.
    private static BlockLight build(int x, int z, OreStages.View view) {
        Map<ChunkPos, MaskedChunk> chunks = new HashMap<>();
        boolean[] affected = {false};
        for (int cx = x - 1; cx <= x + 1; cx++) for (int cz = z - 1; cz <= z + 1; cz++) {
            LevelChunk chunk = view.level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue; // Never load or generate terrain for a player's lighting.
            chunks.put(new ChunkPos(cx, cz), new MaskedChunk(chunk, view));
            chunk.findBlockLightSources((pos, real) -> {
                if (view.replace(real) != real) affected[0] = true;
            });
        }
        if (!affected[0]) return BlockLight.UNCHANGED;

        LevelLightEngine engine = calculate(new LightChunkGetter() {
            @Override public LightChunk getChunkForLighting(int cx, int cz) { return chunks.get(new ChunkPos(cx, cz)); }
            @Override public BlockGetter getLevel() { return view.level; }
        }, chunks, (pos, index) -> chunks.get(pos).chunk.getSection(index).hasOnlyAir());
        return capture(engine, x, z);
    }

    static LevelLightEngine calculate(LightChunkGetter source, Map<ChunkPos, ? extends LightChunk> chunks,
                                     java.util.function.BiPredicate<ChunkPos, Integer> emptySection) {
        LevelLightEngine engine = new LevelLightEngine(source, true, false);
        for (var entry : chunks.entrySet()) {
            var chunk = entry.getValue();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                engine.updateSectionStatus(SectionPos.of(entry.getKey().x(), chunk.getSectionYFromSectionIndex(i), entry.getKey().z()),
                        emptySection.test(entry.getKey(), i));
            }
        }
        engine.runLightUpdates();
        chunks.keySet().forEach(engine::propagateLightSources);
        while (engine.hasLightWork()) engine.runLightUpdates();
        return engine;
    }

    private static BlockLight capture(LevelLightEngine engine, int x, int z) {
        BitSet mask = new BitSet(), empty = new BitSet();
        List<byte[]> layers = new ArrayList<>();
        // Explicit zero layers clear old glow. Skylight remains the source packet's data.
        for (int i = 0; i < engine.getLightSectionCount(); i++) {
            var layer = engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(x, engine.getMinLightSection() + i, z));
            if (layer == null || layer.isEmpty()) empty.set(i);
            else { mask.set(i); layers.add(layer.getData().clone()); }
        }
        return new BlockLight(mask, empty, List.copyOf(layers));
    }

    private record MaskedChunk(LevelChunk chunk, OreStages.View view) implements LightChunk {
        @Override public BlockState getBlockState(BlockPos pos) { return view.replace(chunk.getBlockState(pos)); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return chunk.getBlockEntity(pos); }
        @Override public int getHeight() { return chunk.getHeight(); }
        @Override public int getMinY() { return chunk.getMinY(); }
        @Override public ChunkSkyLightSources getSkyLightSources() { return chunk.getSkyLightSources(); }
        @Override public void findBlockLightSources(BiConsumer<BlockPos, BlockState> consumer) {
            chunk.findBlockLightSources((pos, state) -> {
                BlockState visible = view.replace(state);
                if (visible.getLightEmission(view.level, pos) > 0) consumer.accept(pos, visible);
            });
        }
    }
}
