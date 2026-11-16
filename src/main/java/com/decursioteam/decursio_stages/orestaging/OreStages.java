package com.decursioteam.decursio_stages.orestaging;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.utils.StageUtil;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/** Server-owned views. Rules never modify saved blocks or another player's packet. */
public final class OreStages {
    private record Rule(String stage, boolean fakePlayers, Map<BlockState, BlockState> states) {}
    private static List<Rule> rules = List.of();
    private static long revision = -1;
    private static final Map<UUID, View> VIEWS = new HashMap<>();

    public static final class View {
        final ServerLevel level;
        final Set<ChunkPos> chunks = new HashSet<>();
        final Set<BlockState> refreshStates = new HashSet<>();
        final Deque<ChunkPos> pending = new ArrayDeque<>();
        List<Rule> active = List.of();
        Map<BlockState, BlockState> states = Map.of();
        OreViewCache.Profile profile = new OreViewCache.Profile(Map.of());
        View(ServerLevel level) { this.level = level; }
        public BlockState replace(BlockState state) { return states.getOrDefault(state, state); }
        public boolean empty() { return states.isEmpty(); }
    }

    /** Source emission is allowed: every lit/unlit state maps to the same replacement. */
    public static boolean supportedSource(BlockState state) {
        return !state.hasBlockEntity() && state.getFluidState().isEmpty() && !state.isAir()
                && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static boolean supported(BlockState state) {
        return supportedSource(state) && state.getLightEmission() == 0;
    }

    public static void validate(com.decursioteam.decursio_stages.restrictions.BlockReplacement rule) {
        if (!BuiltInRegistries.BLOCK.containsKey(rule.replacement())
                || !supported(BuiltInRegistries.BLOCK.get(rule.replacement()).defaultBlockState()))
            throw new IllegalArgumentException("Replacement must be a known opaque, unlit full cube without fluid or block entity: " + rule.replacement());
        rule.block().ifPresent(id -> {
            if (!BuiltInRegistries.BLOCK.containsKey(id) || !BuiltInRegistries.BLOCK.get(id).getStateDefinition()
                    .getPossibleStates().stream().allMatch(OreStages::supportedSource))
                throw new IllegalArgumentException("Original block must be a known opaque full cube without fluid or block entity in all states: " + id);
        });
    }

    private static void compile() {
        var registry = RestrictionsData.getRegistry();
        if (revision == registry.revision()) return;
        List<Rule> next = new ArrayList<>();
        registry.getRestrictions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var restriction = entry.getValue();
            Map<BlockState, BlockState> states = new IdentityHashMap<>();
            for (var replacement : restriction.getData().getBlockReplacementList()) {
                if (!BuiltInRegistries.BLOCK.containsKey(replacement.replacement())) {
                    DecursioStages.LOGGER.warn("Unknown ore replacement {} in {}", replacement.replacement(), entry.getKey());
                    continue;
                }
                BlockState target = BuiltInRegistries.BLOCK.get(replacement.replacement()).defaultBlockState();
                if (!supported(target)) {
                    DecursioStages.LOGGER.warn("Ore replacement {} must be an opaque, unlit full cube without fluid or block entity", replacement.replacement());
                    continue;
                }
                List<Block> sources = new ArrayList<>();
                replacement.block().ifPresent(id -> {
                    if (BuiltInRegistries.BLOCK.containsKey(id)) sources.add(BuiltInRegistries.BLOCK.get(id));
                    else DecursioStages.LOGGER.warn("Unknown ore source {} in {}", id, entry.getKey());
                });
                replacement.tag().ifPresent(id -> BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id))
                        .ifPresent(tag -> tag.forEach(holder -> sources.add(holder.value()))));
                for (Block block : sources) {
                    if (block.getStateDefinition().getPossibleStates().stream().allMatch(OreStages::supportedSource)) {
                        for (BlockState state : block.getStateDefinition().getPossibleStates()) states.putIfAbsent(state, target);
                    } else DecursioStages.LOGGER.warn("Unsupported ore source {} in {}; only opaque full cubes without fluid or block entity are supported", BuiltInRegistries.BLOCK.getKey(block), entry.getKey());
                }
            }
            if (!states.isEmpty()) next.add(new Rule(restriction.getData().getStage().toLowerCase(Locale.ROOT),
                    restriction.getSettingsCodec().getApplyToFakePlayers(), Map.copyOf(states)));
        });
        OreViewCache.clear();
        rules = List.copyOf(next);
        revision = registry.revision();
    }

    public static View view(ServerPlayer player) {
        compile();
        ServerLevel level = player.serverLevel();
        View view = VIEWS.computeIfAbsent(player.getUUID(), id -> new View(level));
        if (view.level != level) {
            view = new View(level);
            VIEWS.put(player.getUUID(), view);
        }
        List<Rule> active = rules.stream().filter(rule -> (!(player instanceof FakePlayer) || rule.fakePlayers)
                && !StageUtil.hasStage(player, rule.stage)).toList();
        if (!active.equals(view.active)) {
            Map<BlockState, BlockState> states = new IdentityHashMap<>();
            active.forEach(rule -> rule.states.forEach(states::putIfAbsent));
            view.refreshStates.addAll(view.states.keySet());
            view.refreshStates.addAll(states.keySet());
            view.states = Map.copyOf(states);
            view.profile = new OreViewCache.Profile(view.states);
            view.active = active;
            view.pending.clear();
            view.pending.addAll(view.chunks);
        }
        return view;
    }

    public static BlockState miningState(ServerPlayer player, BlockState real) { return view(player).replace(real); }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        View view = view(player);
        // Limit transition work to four already-sent chunks per tick; never load a chunk for a refresh.
        for (int budget = 0; budget < 4 && !view.pending.isEmpty(); budget++) {
            var pos = view.pending.removeFirst();
            if (!view.chunks.contains(pos)) continue;
            var chunk = player.serverLevel().getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            var sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                var section = sections[i];
                if (!section.maybeHas(view.refreshStates::contains)) continue;
                var changes = new ShortOpenHashSet();
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++)
                    if (view.refreshStates.contains(section.getBlockState(x, y, z))) changes.add((short) ((x << 8) | (z << 4) | y));
                player.connection.send(new ClientboundSectionBlocksUpdatePacket(
                        SectionPos.of(pos.x, chunk.getSectionYFromSectionIndex(i), pos.z), changes, section));
            }
            // Refresh lighting even in chunks with no replaced blocks: neighboring ore can illuminate them.
            player.connection.send(new ClientboundLightUpdatePacket(
                    pos, player.serverLevel().getChunkSource().getLightEngine(), null, null));
        }
        if (view.pending.isEmpty()) view.refreshStates.clear();
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { VIEWS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { VIEWS.clear(); OreViewCache.clear(); rules = List.of(); revision = -1; }
}
