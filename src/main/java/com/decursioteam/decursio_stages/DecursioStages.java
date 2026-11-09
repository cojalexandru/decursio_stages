package com.decursioteam.decursio_stages;

import com.decursioteam.decursio_stages.commands.DecStagesCommands;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.events.*;
import com.decursioteam.decursio_stages.utils.NetworkUtil;
import com.decursioteam.decursio_stages.utils.StagesReload;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.decursioteam.decursio_stages.DecursioStages.MOD_ID;

@Mod(MOD_ID)
public class DecursioStages {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "decursio_stages";
    public static final NetworkUtil NETWORK = new NetworkUtil("decursio_stages:main", "8.0.x");

    public DecursioStages(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.config, "decursio_stages/decursio_stages.toml");

        modBus.addListener(NETWORK::register);
        if (net.neoforged.fml.ModList.get().isLoaded("ftbquests")) {
            modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                    event.enqueueWork(com.decursioteam.decursio_stages.compat.ftbquests.DecursioQuestIntegration::register));
        }
        DecStagesCommands.init(modBus);

        Registry.setupRestrictions();
        Registry.registerRestrictionsList();

        NeoForge.EVENT_BUS.addListener(this::registerReloadListener);
        NeoForge.EVENT_BUS.register(new ItemEvents());
        NeoForge.EVENT_BUS.register(new BlockEvents());
        NeoForge.EVENT_BUS.register(new ContainerEvents());
        NeoForge.EVENT_BUS.register(new MobEvents());
        NeoForge.EVENT_BUS.register(new StructureEvents());
        NeoForge.EVENT_BUS.register(new DimensionEvents());
    }

    private void registerReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "restrictions"), new StagesReload());
    }
}
