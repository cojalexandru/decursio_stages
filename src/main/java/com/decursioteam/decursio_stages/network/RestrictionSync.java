package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = DecursioStages.MOD_ID)
public final class RestrictionSync {
    private static boolean pending;
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            DecursioStages.NETWORK.sendToPlayer(player, snapshot());
    }
    @SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && pending) {
            pending = false;
            DecursioStages.NETWORK.sendToAllPlayers(snapshot());
        }
    }
    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { pending = false; }
    private static SyncRestrictionsMessage snapshot() {
        return new SyncRestrictionsMessage(RestrictionsData.getRegistry().getRawRestrictions());
    }
    public static void broadcast() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> pending = true);
    }
}
