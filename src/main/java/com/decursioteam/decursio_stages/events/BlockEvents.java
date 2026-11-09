package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.decursioteam.decursio_stages.utils.Utils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Locale;

import static com.decursioteam.decursio_stages.utils.ResourceUtil.check;
import static com.decursioteam.decursio_stages.utils.ResourceUtil.getApplyToFakePlayer;
import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;

public class BlockEvents
{
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event){
        if(event.getEntity() instanceof Player player) {
            Registry.getRestrictions().forEach(restriction -> {
                if(!getApplyToFakePlayer(restriction) && player instanceof FakePlayer) return;

                String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage().toLowerCase(Locale.ROOT);
                if (!player.level().isClientSide && hasStage(player, stage)) {
                    StructureManager structureManager = event.getLevel().getServer().getLevel(player.level().dimension()).structureManager();

                    if (!RestrictionsData.getRestrictionData(restriction).getData().getStructureList().isEmpty()) {
                        RestrictionsData.getRestrictionData(restriction).getData().getStructureList().forEach(structureRestriction -> {
                            Structure structure = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureRestriction.getStructure());
                            if (structure != null && structureManager.getStructureAt(event.getPos(), structure).isValid()) {
                                ResourceLocation registryName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
                                if (!structureRestriction.getCanPlaceBlock() && !structureRestriction.getCanPlaceBlockList().get().contains(registryName)) {
                                    event.setCanceled(true);
                                }
                            }
                        });
                    }
                }

                if (!RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getUsableBlocks() && !hasStage(player, stage)) {
                    if (check(restriction, new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem()), ResourceUtil.CHECK_TYPES.ALL)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Utils.BLOCK_INTERACT_WARN, true);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public void onPlayerInteractWithBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() != null) {
            Player player = event.getEntity();
            Registry.getRestrictions().forEach(restriction -> {
                if(!getApplyToFakePlayer(restriction) && player instanceof FakePlayer) return;

                String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage().toLowerCase(Locale.ROOT);
                if(!player.level().isClientSide && hasStage(player, stage)) {
                    StructureManager structureManager = event.getLevel().getServer().getLevel(player.level().dimension()).structureManager();

                    if (!RestrictionsData.getRestrictionData(restriction).getData().getStructureList().isEmpty()) {
                        RestrictionsData.getRestrictionData(restriction).getData().getStructureList().forEach(structureRestriction -> {
                            Structure structure = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureRestriction.getStructure());
                            if (structure != null && structureManager.getStructureAt(event.getPos(), structure).isValid()) {
                                ResourceLocation registryName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
                                if (!structureRestriction.getCanUseBlock() && !structureRestriction.getUseBlockList().get().contains(registryName)) {
                                    event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                                }
                            }
                        });
                    }
                }

                if (!RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getUsableBlocks() && !hasStage(player, stage)) {
                    if (check(restriction, new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem()), ResourceUtil.CHECK_TYPES.ALL)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Utils.BLOCK_INTERACT_ERROR, true);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public void explosionEvent(ExplosionEvent.Detonate event) {
        if (event.getLevel().getServer() == null) {
            return;
        }

        var ref = new Object() {
            Player closestPlayer = null;
        };

        for (ServerPlayer player : event.getLevel().getServer().getPlayerList().getPlayers()) {
            if (ref.closestPlayer == null) {
                ref.closestPlayer = player;
            }
            if (player.distanceToSqr(event.getExplosion().center()) < ref.closestPlayer.distanceToSqr(event.getExplosion().center())) {
                ref.closestPlayer = player;
            }
        }

        if (ref.closestPlayer == null) {
            return;
        }

        Registry.getRestrictions().forEach(restriction -> {
            if (!getApplyToFakePlayer(restriction) && ref.closestPlayer instanceof FakePlayer) {
                return;
            }

            String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage();
            if (!ref.closestPlayer.level().isClientSide && hasStage(ref.closestPlayer, stage)) {
                StructureManager structureManager = event.getLevel().getServer().getLevel(event.getLevel().dimension()).structureManager();
                if (!RestrictionsData.getRestrictionData(restriction).getData().getStructureList().isEmpty()) {
                    RestrictionsData.getRestrictionData(restriction).getData().getStructureList().forEach(structureRestriction -> {
                        Structure structure = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureRestriction.getStructure());
                        event.getAffectedBlocks().removeIf(blockPos -> {
                            if (structure == null || !structureManager.getStructureAt(blockPos, structure).isValid()) return false;
                            ResourceLocation registryName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(blockPos).getBlock());
                            return !structureRestriction.getCanBreakBlock() && !structureRestriction.getBreakBlockList().orElse(java.util.List.of()).contains(registryName);
                        });
                    });
                }
            }
        });
    }

    // credits to https://github.com/mangdags
    @SubscribeEvent
    public void onPlayerDestroyBlock(BlockEvent.BreakEvent event) {
        Registry.getRestrictions().forEach(restriction -> {
            if(!getApplyToFakePlayer(restriction) && event.getPlayer() instanceof FakePlayer) return;

            String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage();
            if(!event.getPlayer().level().isClientSide && hasStage(event.getPlayer(), stage)) {
                StructureManager structureManager = event.getLevel().getServer().getLevel(event.getPlayer().level().dimension()).structureManager();
                if (!RestrictionsData.getRestrictionData(restriction).getData().getStructureList().isEmpty()) {
                    RestrictionsData.getRestrictionData(restriction).getData().getStructureList().forEach(structureRestriction -> {
                        Structure structure = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureRestriction.getStructure());
                        if (structure != null && structureManager.getStructureAt(event.getPos(), structure).isValid()) {
                            ResourceLocation registryName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
                            if (!structureRestriction.getCanBreakBlock() && !structureRestriction.getBreakBlockList().get().contains(registryName)) {
                                event.setCanceled(true);
                            }
                        }
                    });
                }
            }
            if (!RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getDestroyableBlocks() && !hasStage(event.getPlayer(), stage)) {
                if (check(restriction, new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem()), ResourceUtil.CHECK_TYPES.ALL)) {
                    event.setCanceled(true);
                    event.getPlayer().displayClientMessage(Utils.BLOCK_DESTROY_ERROR, true);
                }
            }
        });
    }

    @SubscribeEvent
    public void onPlayerInteractWithBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() != null) {
            Player player = event.getEntity();
            Registry.getRestrictions().forEach(restriction -> {
                if(!getApplyToFakePlayer(restriction) && player instanceof FakePlayer) return;

                String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage().toLowerCase(Locale.ROOT);
                if(!player.level().isClientSide && hasStage(player, stage)) {
                    StructureManager structureManager = event.getLevel().getServer().getLevel(player.level().dimension()).structureManager();
                    if (!RestrictionsData.getRestrictionData(restriction).getData().getStructureList().isEmpty()) {
                        RestrictionsData.getRestrictionData(restriction).getData().getStructureList().forEach(structureRestriction -> {
                            Structure structure = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureRestriction.getStructure());
                            if (structure != null && structureManager.getStructureAt(event.getPos(), structure).isValid()) {
                                ResourceLocation registryName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
                                if (!structureRestriction.getCanBreakBlock() && !structureRestriction.getBreakBlockList().get().contains(registryName)) {
                                    event.setCanceled(true);
                                }
                            }
                        });
                    }
                }

                if (!RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getUsableBlocks() && !hasStage(player, stage)) {
                    if (check(restriction, new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem()), ResourceUtil.CHECK_TYPES.ALL)) {
                        player.displayClientMessage(Utils.BLOCK_INTERACT_WARN, true);
                    }
                }
                if (!RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getUsableItems() && !hasStage(player, stage)) {
                    if (check(restriction, event.getItemStack(), ResourceUtil.CHECK_TYPES.ALL)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Utils.ITEM_INTERACT_ERROR, true);
                    }
                }
            });
        }
    }
}
