package com.decursioteam.decursio_stages;

import com.decursioteam.decursio_stages.commands.DecStagesCommands;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.events.*;
import com.decursioteam.decursio_stages.network.RestrictionEditorServer;
import com.decursioteam.decursio_stages.network.RestrictionSync;
import com.decursioteam.decursio_stages.network.messages.*;
import com.decursioteam.decursio_stages.orestaging.OreStages;
import com.decursioteam.decursio_stages.utils.NetworkUtil;
import com.decursioteam.decursio_stages.utils.StagesReload;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.decursioteam.decursio_stages.DecursioStages.MOD_ID;

@Mod(MOD_ID)
public class DecursioStages {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "decursio_stages";
    public static final NetworkUtil NETWORK = new NetworkUtil("decursio_stages:main", "8.4.x");

    public DecursioStages() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.config, "decursio_stages/decursio_stages.toml");

        NETWORK.registerEnqueuedMessage(SyncStagesMessage.class,
                SyncStagesMessage::encode,
                SyncStagesMessage::decode,
                (message, context) -> {
                    if (context.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) return;
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SyncStagesMessage.handle(message));
                });

        NETWORK.registerEnqueuedMessage(OpenRestrictEditorMessage.class,
                OpenRestrictEditorMessage::encode,
                OpenRestrictEditorMessage::decode,
                (message, context) -> {
                    if (context.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) return;
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.decursioteam.decursio_stages.client.screens.RestrictEditorClient.open(message));
                });

        NETWORK.registerEnqueuedMessage(EditRestrictionMessage.class,
                EditRestrictionMessage::encode,
                EditRestrictionMessage::decode,
                (message, context) -> {
                    if (context.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) return;
                    RestrictionEditorServer.handle(message, context.get().getSender());
                });

        NETWORK.registerEnqueuedMessage(SyncRestrictionsMessage.class,
                SyncRestrictionsMessage::encode,
                SyncRestrictionsMessage::decode,
                (message, context) -> {
                    if (context.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) return;
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.decursioteam.decursio_stages.client.ClientRestrictionSync.apply(message));
                });

        NETWORK.registerEnqueuedMessage(RequestRestrictionsMessage.class,
                RequestRestrictionsMessage::encode,
                RequestRestrictionsMessage::decode,
                (message, context) -> {
                    if (context.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) return;
                    var player = context.get().getSender();
                    if (player != null) RestrictionSync.recover(player);
                });

        NETWORK.registerEnqueuedMessage(SyncRecipeVisibilityMessage.class,
                SyncRecipeVisibilityMessage::encode,
                SyncRecipeVisibilityMessage::decode,
                (message, context) -> {
                    if (context.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) return;
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.decursioteam.decursio_stages.client.ClientRecipeVisibility.apply(message));
                });

        DecStagesCommands.init();

        Registry.setupRestrictions();
        Registry.registerRestrictionsList();

        MinecraftForge.EVENT_BUS.addListener(this::registerReloadListener);
        MinecraftForge.EVENT_BUS.register(new ItemEvents());
        MinecraftForge.EVENT_BUS.register(new BlockEvents());
        MinecraftForge.EVENT_BUS.register(new ContainerEvents());
        MinecraftForge.EVENT_BUS.register(new MobEvents());
        MinecraftForge.EVENT_BUS.register(new StructureEvents());
        MinecraftForge.EVENT_BUS.register(new DimensionEvents());
        MinecraftForge.EVENT_BUS.register(new OreStages());

        CommonConfig.loadConfig(CommonConfig.config, FMLPaths.CONFIGDIR.get().resolve("decursio_stages/decursio_stages.toml").toString());
    }

    private void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(new StagesReload());
    }
}