package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.network.RestrictionSyncState;
import com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class ClientRestrictionSync {
    private static final RestrictionSyncState STATE = new RestrictionSyncState();
    public static void apply(SyncRestrictionsMessage message) {
        try {
            var result = STATE.apply(message, update -> RestrictionsData.applyClientChanges(update.restrictions(), update.removed(), update.full()));
            if (result == RestrictionSyncState.Result.REQUEST_SNAPSHOT) sendSnapshotRequest();
            if (result != RestrictionSyncState.Result.APPLIED) return;
            java.util.Set<String> changed = new java.util.HashSet<>(message.restrictions().keySet());
            changed.addAll(message.removed());
            NeoForge.EVENT_BUS.post(new com.decursioteam.decursio_stages.events.RestrictionsChangedEvent(changed, message.full()));
        } catch (RuntimeException error) {
            DecursioStages.LOGGER.error("Could not apply restriction revision {}", message.revision(), error);
            requestSnapshot();
        }
    }
    private static void requestSnapshot() {
        if (STATE.recover()) sendSnapshotRequest();
    }
    private static void sendSnapshotRequest() {
        DecursioStages.NETWORK.sendToServer(new com.decursioteam.decursio_stages.network.messages.RequestRestrictionsMessage());
    }
    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null && STATE.retry()) sendSnapshotRequest();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATE.reset();
        RestrictionsData.clearClientSnapshot();
        com.decursioteam.decursio_stages.utils.StagesHandler.clearClientData();
    }
}
