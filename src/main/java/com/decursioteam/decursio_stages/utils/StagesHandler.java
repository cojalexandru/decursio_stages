package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.StagesData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.decursioteam.decursio_stages.DecursioStages.LOGGER;

@Mod.EventBusSubscriber(modid = "decursio_stages")
public class StagesHandler {

    private static final Map<UUID, IStagesData> GLOBAL_STAGE_DATA = new HashMap<>();
    @OnlyIn(Dist.CLIENT)
    private static IStagesData clientData;

    public static Set<String> getStages(){
        HashSet<String> stages = new HashSet<>();
        RestrictionsData.getRegistry().getRawRestrictions().forEach((name, rawJSON) -> {
            stages.add(rawJSON.getAsJsonObject("Restriction Data").get("stage").getAsString());
        });
        return stages;
    }

    @SubscribeEvent
    public static void onPlayerLoad(PlayerEvent.LoadFromFile event) {

        final File playerFile = getPlayerFile(event.getPlayerDirectory(), event.getPlayerUUID());
        final IStagesData playerData = new StagesData();

        if (playerFile.exists()) {

            try {

                final CompoundTag tag = NbtIo.read(playerFile);
                playerData.readFromNBT(tag);
                DecursioStages.LOGGER.debug("Loaded {} stages for {}.", playerData.getStages().size(), event.getEntity().getName().getString());
            }

            catch (final IOException e) {

                DecursioStages.LOGGER.error("Could not read player data for {}.", event.getEntity().getName().getString());
                DecursioStages.LOGGER.catching(e);
            }
        }

        GLOBAL_STAGE_DATA.put(event.getEntity().getUUID(), playerData);
    }

    @SubscribeEvent
    public static void onPlayerSave (PlayerEvent.SaveToFile event) {

        final UUID playerUUID = event.getEntity().getUUID();

        if (GLOBAL_STAGE_DATA.containsKey(playerUUID)) {

            final IStagesData playerData = getPlayerData(playerUUID);
            final File playerFile = getPlayerFile(event.getPlayerDirectory(), event.getPlayerUUID());
            assert playerData != null;
            final CompoundTag tag = playerData.writeToNBT();

            if (tag != null) {
                try {
                    NbtIo.write(tag, playerFile);
                    LOGGER.info("Saved {} stages for {}.", playerData.getStages().size(), event.getEntity().getName().getString());
                }

                catch (final IOException e) {
                    if(CommonConfig.debugMode.get()) {
                        LOGGER.error("Could not write player data for {}.", playerFile.getName());
                        LOGGER.catching(e);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn (PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            if(CommonConfig.debugMode.get()) LOGGER.info("Syncing {} player data with the client", event.getEntity().getName().getString());
            StageUtil.syncPlayer((ServerPlayer) event.getEntity());
        }
    }

    @Nullable
    public static IStagesData getPlayerData (UUID uuid) {
        return GLOBAL_STAGE_DATA.get(uuid);
    }

    private static File getPlayerFile (File playerDir, String uuid) {
        final File saveDir = new File(playerDir, "decursio_stages");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        return new File(saveDir, uuid + ".dat");
    }

    @OnlyIn(Dist.CLIENT)
    public static IStagesData getClientData () {
        return clientData;
    }

    @OnlyIn(Dist.CLIENT)
    public static void setClientData (IStagesData stageData) {
        clientData = stageData;
    }
}
