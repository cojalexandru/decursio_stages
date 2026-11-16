package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.events.RestrictionsChangedEvent;
import com.decursioteam.decursio_stages.network.RestrictionSyncState;
import com.decursioteam.decursio_stages.network.messages.RequestRestrictionsMessage;
import com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage;
import com.decursioteam.decursio_stages.utils.StagesHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class ClientRestrictionSync {
    private static final RestrictionSyncState STATE = new RestrictionSyncState();

    public static void apply(SyncRestrictionsMessage message) {
        try {
            var result = STATE.apply(message, update -> RestrictionsData.applyClientChanges(update.restrictions(), update.removed(), update.full()));
            if (result == RestrictionSyncState.Result.REQUEST_SNAPSHOT) sendSnapshotRequest();
            if (result != RestrictionSyncState.Result.APPLIED) return;
            Set<String> changed = new HashSet<>(message.restrictions().keySet());
            changed.addAll(message.removed());
            MinecraftForge.EVENT_BUS.post(new RestrictionsChangedEvent(changed, message.full()));
        } catch (RuntimeException error) {
            DecursioStages.LOGGER.error("Could not apply restriction revision {}", message.revision(), error);
            requestSnapshot();
        }
    }

    private static void requestSnapshot() {
        if (STATE.recover()) sendSnapshotRequest();
    }

    private static void sendSnapshotRequest() {
        DecursioStages.NETWORK.sendToServer(new RequestRestrictionsMessage());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().getConnection() != null && STATE.retry()) sendSnapshotRequest();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATE.reset();
        RestrictionsData.clearClientSnapshot();
        StagesHandler.clearClientData();
    }

    private ClientRestrictionSync() {}
}
