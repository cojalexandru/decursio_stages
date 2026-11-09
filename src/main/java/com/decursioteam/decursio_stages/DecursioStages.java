package com.decursioteam.decursio_stages;

import com.decursioteam.decursio_stages.client.HUDOverlay;
import com.decursioteam.decursio_stages.commands.DecStagesCommands;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.events.*;
import com.decursioteam.decursio_stages.network.ClientPacketHandler;
import com.decursioteam.decursio_stages.network.MessageHandlers;
import com.decursioteam.decursio_stages.network.ServerPacketHandler;
import com.decursioteam.decursio_stages.network.RestrictionEditorServer;
import com.decursioteam.decursio_stages.network.messages.EditRestrictionMessage;
import com.decursioteam.decursio_stages.network.messages.OpenRestrictEditorMessage;
import com.decursioteam.decursio_stages.network.messages.SyncStagesMessage;
import com.decursioteam.decursio_stages.utils.NetworkUtil;
import com.decursioteam.decursio_stages.utils.StagesReload;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.decursioteam.decursio_stages.DecursioStages.MOD_ID;

@Mod(MOD_ID)
public class DecursioStages {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "decursio_stages";
    public static final NetworkUtil NETWORK = new NetworkUtil("decursio_stages:main", "8.0.x");

    public DecursioStages() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.config, "decursio_stages/decursio_stages.toml");

        NETWORK.registerEnqueuedMessage(SyncStagesMessage.class,
                ServerPacketHandler::encodeStageMessage,
                ClientPacketHandler::decodeStageMessage,
                ClientPacketHandler::processSyncStagesMessage);

        NETWORK.registerEnqueuedMessage(OpenRestrictEditorMessage.class,
                OpenRestrictEditorMessage::encode,
                OpenRestrictEditorMessage::decode,
                MessageHandlers::handleOpenRestrictEditor);

        NETWORK.registerEnqueuedMessage(EditRestrictionMessage.class,
                EditRestrictionMessage::encode,
                EditRestrictionMessage::decode,
                RestrictionEditorServer::handle);

        NETWORK.registerEnqueuedMessage(com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage.class,
                com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage::encode,
                com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage::decode,
                (message, context) -> {
                    if (context.get().getDirection() != net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT) return;
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> com.decursioteam.decursio_stages.client.ClientRestrictionSync.apply(message));
                });

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

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


        MinecraftForge.EVENT_BUS.register(this);
        CommonConfig.loadConfig(CommonConfig.config, FMLPaths.CONFIGDIR.get().resolve("decursio_stages/decursio_stages.toml").toString());
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // Register client-side event handlers
        MinecraftForge.EVENT_BUS.register(HUDOverlay.class);
    }

    private void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(new StagesReload());
    }
}