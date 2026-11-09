package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage;
import com.decursioteam.decursio_stages.utils.StagesHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class ClientRestrictionSync {
    public static void apply(SyncRestrictionsMessage message) {
        RestrictionsData.applyClientSnapshot(message.restrictions());
        if (StagesHandler.getClientData() != null)
            MinecraftForge.EVENT_BUS.post(new SyncStagesEvent(StagesHandler.getClientData()));
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RestrictionsData.clearClientSnapshot();
    }
}
