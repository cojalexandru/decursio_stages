package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.datagen.Restrictions;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.restrictions.StructureRestriction;
import com.decursioteam.decursio_stages.utils.CompiledRestriction;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.decursioteam.decursio_stages.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;

public class BlockEvents {

    private static boolean appliesTo(Restrictions restriction, Player player) {
        return !(player instanceof FakePlayer) || restriction.getSettingsCodec().getApplyToFakePlayers();
    }

    private static boolean blocksStructureAction(
            LevelAccessor levelAccessor,
            BlockPos pos,
            Restrictions restriction,
            Predicate<StructureRestriction> allowedFlag,
            Function<StructureRestriction, Optional<List<ResourceLocation>>> allowedBlocks) {
        List<StructureRestriction> structures = restriction.getData().getStructureList();
        if (structures.isEmpty() || !(levelAccessor instanceof Level level) || level.getServer() == null) {
            return false;
        }
        ServerLevel serverLevel = level.getServer().getLevel(level.dimension());
        if (serverLevel == null) {
            return false;
        }
        StructureManager structureManager = serverLevel.structureManager();
        var structureRegistry = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(levelAccessor.getBlockState(pos).getBlock());

        for (StructureRestriction rule : structures) {
            Structure structure = structureRegistry.get(rule.getStructure());
            if (structure != null && structureManager.getStructureAt(pos, structure).isValid()) {
                if (!allowedFlag.test(rule) && !allowedBlocks.apply(rule).orElse(List.of()).contains(blockId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ServerPlayer nearestPlayer(MinecraftServer server, Vec3 pos) {
        ServerPlayer closest = null;
        double closestDist = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            double dist = player.distanceToSqr(pos);
            if (closest == null || dist < closestDist) {
                closest = player;
                closestDist = dist;
            }
        }
        return closest;
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        var registry = RestrictionsData.getRegistry();
        ItemStack blockStack = new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem());

        registry.getRestrictions().forEach((name, restriction) -> {
            if (!appliesTo(restriction, player)) return;

            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            boolean unlocked = hasStage(player, stage);
            if (!player.level().isClientSide && unlocked
                    && blocksStructureAction(event.getLevel(), event.getPos(), restriction,
                    StructureRestriction::getCanPlaceBlock, StructureRestriction::getCanPlaceBlockList)) {
                event.setCanceled(true);
            }

            if (!restriction.getSettingsCodec().getUsableBlocks() && !unlocked
                    && registry.compiled(name).matches(blockStack, ResourceUtil.CHECK_TYPES.ALL)) {
                event.setCanceled(true);
                player.displayClientMessage(Utils.BLOCK_INTERACT_WARN, true);
            }
        });
    }

    @SubscribeEvent
    public void onPlayerInteractWithBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null) return;

        var registry = RestrictionsData.getRegistry();
        ItemStack blockStack = new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem());

        registry.getRestrictions().forEach((name, restriction) -> {
            if (!appliesTo(restriction, player)) return;

            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            boolean unlocked = hasStage(player, stage);
            if (!player.level().isClientSide && unlocked
                    && blocksStructureAction(event.getLevel(), event.getPos(), restriction,
                    StructureRestriction::getCanUseBlock, StructureRestriction::getUseBlockList)) {
                event.setUseBlock(TriState.FALSE);
            }

            if (!restriction.getSettingsCodec().getUsableBlocks() && !unlocked
                    && registry.compiled(name).matches(blockStack, ResourceUtil.CHECK_TYPES.ALL)) {
                event.setCanceled(true);
                player.displayClientMessage(Utils.BLOCK_INTERACT_ERROR, true);
            }
        });
    }

    @SubscribeEvent
    public void explosionEvent(ExplosionEvent.Detonate event) {
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) return;

        ServerPlayer closestPlayer = nearestPlayer(server, event.getExplosion().center());
        if (closestPlayer == null) return;

        RestrictionsData.getRegistry().getRestrictions().values().forEach(restriction -> {
            if (!appliesTo(restriction, closestPlayer)) return;

            List<StructureRestriction> structures = restriction.getData().getStructureList();
            if (structures.isEmpty()) return;

            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            if (closestPlayer.level().isClientSide || !hasStage(closestPlayer, stage)) return;

            ServerLevel level = server.getLevel(event.getLevel().dimension());
            if (level == null) return;
            StructureManager structureManager = level.structureManager();
            var structureRegistry = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE);

            for (StructureRestriction rule : structures) {
                Structure structure = structureRegistry.get(rule.getStructure());
                event.getAffectedBlocks().removeIf(blockPos -> {
                    if (structure == null || !structureManager.getStructureAt(blockPos, structure).isValid()) return false;
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(blockPos).getBlock());
                    return !rule.getCanBreakBlock() && !rule.getBreakBlockList().orElse(List.of()).contains(blockId);
                });
            }
        });
    }

    // credits to https://github.com/mangdags
    @SubscribeEvent
    public void onPlayerDestroyBlock(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        var registry = RestrictionsData.getRegistry();
        ItemStack blockStack = new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem());

        registry.getRestrictions().forEach((name, restriction) -> {
            if (!appliesTo(restriction, player)) return;

            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            boolean unlocked = hasStage(player, stage);
            if (!player.level().isClientSide && unlocked
                    && blocksStructureAction(event.getLevel(), event.getPos(), restriction,
                    StructureRestriction::getCanBreakBlock, StructureRestriction::getBreakBlockList)) {
                event.setCanceled(true);
            }

            if (!restriction.getSettingsCodec().getDestroyableBlocks() && !unlocked
                    && registry.compiled(name).matches(blockStack, ResourceUtil.CHECK_TYPES.ALL)) {
                event.setCanceled(true);
                player.displayClientMessage(Utils.BLOCK_DESTROY_ERROR, true);
            }
        });
    }

    @SubscribeEvent
    public void onPlayerInteractWithBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player == null) return;

        var registry = RestrictionsData.getRegistry();
        ItemStack blockStack = new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem());

        registry.getRestrictions().forEach((name, restriction) -> {
            if (!appliesTo(restriction, player)) return;

            String stage = restriction.getData().getStage().toLowerCase(Locale.ROOT);
            boolean unlocked = hasStage(player, stage);
            if (!player.level().isClientSide && unlocked
                    && blocksStructureAction(event.getLevel(), event.getPos(), restriction,
                    StructureRestriction::getCanBreakBlock, StructureRestriction::getBreakBlockList)) {
                event.setCanceled(true);
            }

            CompiledRestriction compiled = registry.compiled(name);
            if (!restriction.getSettingsCodec().getUsableBlocks() && !unlocked
                    && compiled.matches(blockStack, ResourceUtil.CHECK_TYPES.ALL)) {
                player.displayClientMessage(Utils.BLOCK_INTERACT_WARN, true);
            }
            if (!restriction.getSettingsCodec().getUsableItems() && !unlocked
                    && compiled.matches(event.getItemStack(), ResourceUtil.CHECK_TYPES.ALL)) {
                event.setCanceled(true);
                player.displayClientMessage(Utils.ITEM_INTERACT_ERROR, true);
            }
        });
    }
}
