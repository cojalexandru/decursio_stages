package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.events.UpdateStageEvent;
import com.decursioteam.decursio_stages.network.messages.SyncStagesMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import javax.annotation.Nullable;
import java.util.*;

public class StageUtil {
    public static boolean hasStage (Player player, String stage) {

        return hasStage(player, getPlayerData(player), stage);
    }

    public static boolean hasStage (Player player, @Nullable IStagesData data, String stage) {

        if (data != null) {

            final UpdateStageEvent.Check event = new UpdateStageEvent.Check(player, stage, data.hasStage(stage));
            MinecraftForge.EVENT_BUS.post(event);
            return event.hasStage();
        }

        return false;
    }

    public static boolean hasAnyOf (Player player, String... stages) {

        return hasAnyOf(player, getPlayerData(player), stages);
    }

    public static boolean hasAnyOf (Player player, Collection<String> stages) {

        return hasAnyOf(player, getPlayerData(player), stages);
    }

    public static boolean hasAnyOf (Player player, @Nullable IStagesData data, Collection<String> stages) {

        return stages.stream().anyMatch(stage -> hasStage(player, data, stage.toLowerCase(Locale.ROOT)));
    }

    public static boolean hasAnyOf (Player player, @Nullable IStagesData data, String... stages) {

        return Arrays.stream(stages).anyMatch(stage -> hasStage(player, data, stage.toLowerCase(Locale.ROOT)));
    }

    public static boolean hasAllOf (Player player, String... stages) {

        return hasAllOf(player, getPlayerData(player), stages);
    }

    public static boolean hasAllOf (Player player, Collection<String> stages) {

        return hasAllOf(player, getPlayerData(player), stages);
    }

    public static boolean hasAllOf (Player player, @Nullable IStagesData data, Collection<String> stages) {

        return stages.stream().allMatch(stage -> hasStage(player, data, stage.toLowerCase(Locale.ROOT)));
    }


    public static boolean hasAllOf (Player player, @Nullable IStagesData data, String... stages) {

        return Arrays.stream(stages).allMatch(stage -> hasStage(player, data, stage.toLowerCase(Locale.ROOT)));
    }


    public static void addStage (ServerPlayer player, String stage) {

        if (!MinecraftForge.EVENT_BUS.post(new UpdateStageEvent.Add(player, stage.toLowerCase(Locale.ROOT)))) {

            final IStagesData data = getPlayerData(player);

            if (data != null) {

                data.addStage(stage);
                syncPlayer(player);
                MinecraftForge.EVENT_BUS.post(new UpdateStageEvent.Added(player, stage.toLowerCase(Locale.ROOT)));
            }
        }
    }

    public static void removeStage (ServerPlayer player, String stage) {

        if (!MinecraftForge.EVENT_BUS.post(new UpdateStageEvent.Remove(player, stage.toLowerCase(Locale.ROOT)))) {

            final IStagesData data = getPlayerData(player);

            if (data != null) {

                data.removeStage(stage);
                syncPlayer(player);
                MinecraftForge.EVENT_BUS.post(new UpdateStageEvent.Removed(player, stage.toLowerCase(Locale.ROOT)));
            }
        }
    }

    public static int clearStages (ServerPlayer player) {

        final IStagesData stageInfo = StageUtil.getPlayerData(player);

        if (stageInfo != null) {

            final int stageCount = stageInfo.getStages().size();
            stageInfo.clear();
            syncPlayer(player);
            MinecraftForge.EVENT_BUS.post(new UpdateStageEvent.Cleared(player, stageInfo));
            return stageCount;
        }

        return 0;
    }

    @Nullable
    public static IStagesData getPlayerData (Player player) {

        if (player != null) {

            if (player instanceof ServerPlayer) {

                return StagesHandler.getPlayerData(player.getUUID());
            }

            else if (EffectiveSide.get().isClient()) {

                return StagesHandler.getClientData();
            }
        }

        return null;
    }

    public static void syncPlayer (ServerPlayer player) {

        final IStagesData info = StageUtil.getPlayerData(player);

        if (info != null) {

            DecursioStages.LOGGER.info("Syncing {} stages for {}.", info.getStages().size(), player.getName().getString());
            DecursioStages.NETWORK.sendToPlayer(player, new SyncStagesMessage(info.getStages()));
        }
    }

    public static Set<String> getStages(){
        HashSet<String> stages = new HashSet<>();
        RestrictionsData.getRegistry().getRawRestrictions().forEach((name, rawJSON) -> {
            stages.add(rawJSON.getAsJsonObject("Restriction Data").get("stage").getAsString());
        });
        return stages;
    }
}
