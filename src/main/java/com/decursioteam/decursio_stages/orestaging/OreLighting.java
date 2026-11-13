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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Rebuild block light with vanilla propagation against the player's visible block states. */
public final class OreLighting {
    private OreLighting() {}

    // Block light travels at most 14 blocks from a level-15 emitter. A one-chunk border
    // therefore contains every source/path that can affect the central chunk.
    public static void rewrite(ClientboundLightUpdatePacketData data, int x, int z, OreStages.View view) {
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
        if (!affected[0]) return;

        LevelLightEngine engine = calculate(new LightChunkGetter() {
            @Override public LightChunk getChunkForLighting(int cx, int cz) { return chunks.get(new ChunkPos(cx, cz)); }
            @Override public BlockGetter getLevel() { return view.level; }
        }, chunks, (pos, index) -> chunks.get(pos).chunk.getSection(index).hasOnlyAir());
        applyBlockLight(data, engine, x, z);
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

    static void applyBlockLight(ClientboundLightUpdatePacketData data, LevelLightEngine engine, int x, int z) {
        data.getBlockYMask().clear();
        data.getEmptyBlockYMask().clear();
        data.getBlockUpdates().clear();
        // Include explicit zero layers: omitting them would leave the old glow in the client.
        for (int i = 0; i < engine.getLightSectionCount(); i++) {
            var layer = engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(x, engine.getMinLightSection() + i, z));
            if (layer == null || layer.isEmpty()) data.getEmptyBlockYMask().set(i);
            else {
                data.getBlockYMask().set(i);
                data.getBlockUpdates().add(layer.getData().clone());
            }
        }
        // Skylight is unchanged: both original and replacement are opaque full cubes.
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
