package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage;
import com.decursioteam.decursio_stages.utils.StagesHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class ClientRestrictionSync {
    public static void apply(SyncRestrictionsMessage message) {
        RestrictionsData.applyClientSnapshot(message.restrictions());
        if (StagesHandler.getClientData() != null)
            NeoForge.EVENT_BUS.post(new SyncStagesEvent(StagesHandler.getClientData()));
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RestrictionsData.clearClientSnapshot();
    }
}
