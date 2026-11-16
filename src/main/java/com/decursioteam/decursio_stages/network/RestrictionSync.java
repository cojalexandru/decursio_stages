package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

@Mod.EventBusSubscriber(modid = DecursioStages.MOD_ID)
public final class RestrictionSync {
    private static boolean pending;
    private static long revision;
    private static long dataRevision = -1;
    private static Map<String, JsonObject> published = Map.of();
    private static SyncRestrictionsMessage snapshot;
    private static final Set<UUID> initialized = new HashSet<>();
    private static final Set<UUID> deferredRecovery = new HashSet<>();
    private static final Map<UUID, Long> lastRecovery = new HashMap<>();

    private static void publish() {
        var registry = RestrictionsData.getRegistry();
        if (!pending && dataRevision == registry.revision()) return;
        var current = registry.getRawRestrictions();
        Map<String, JsonObject> changed = new LinkedHashMap<>();
        current.forEach((id, json) -> { if (!json.equals(published.get(id))) changed.put(id, json); });
        Set<String> removed = new HashSet<>(published.keySet()); removed.removeAll(current.keySet());
        if (!changed.isEmpty() || !removed.isEmpty()) {
            var delta = new SyncRestrictionsMessage(revision, ++revision, false, changed, removed);
            Map<String, JsonObject> copy = new LinkedHashMap<>(); current.forEach((id, json) -> copy.put(id, json.deepCopy()));
            published = Collections.unmodifiableMap(copy); snapshot = null;
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) for (var player : server.getPlayerList().getPlayers())
                if (initialized.contains(player.getUUID())) DecursioStages.NETWORK.sendToPlayer(player, delta);
        }
        dataRevision = registry.revision();
        pending = false;
    }

    public static void sendSnapshot(ServerPlayer player) {
        publish();
        if (snapshot == null) snapshot = new SyncRestrictionsMessage(-1, revision, true, published, Set.of());
        DecursioStages.NETWORK.sendToPlayer(player, snapshot);
        initialized.add(player.getUUID());
    }

    public static void recover(ServerPlayer player) {
        long tick = player.server.getTickCount();
        Long previous = lastRecovery.get(player.getUUID());
        if (previous != null && tick - previous < 20) { deferredRecovery.add(player.getUUID()); return; }
        lastRecovery.put(player.getUUID(), tick);
        sendSnapshot(player);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendSnapshot(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        initialized.remove(event.getEntity().getUUID());
        lastRecovery.remove(event.getEntity().getUUID());
        deferredRecovery.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pending) publish();
        var requests = deferredRecovery.iterator();
        while (requests.hasNext()) {
            UUID id = requests.next();
            if ((long) event.getServer().getTickCount() - lastRecovery.getOrDefault(id, Long.MIN_VALUE / 2) < 20) continue;
            requests.remove();
            var player = event.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                lastRecovery.put(id, (long) event.getServer().getTickCount());
                sendSnapshot(player);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        pending = false; revision = 0; dataRevision = -1; published = Map.of();
        snapshot = null; initialized.clear(); lastRecovery.clear(); deferredRecovery.clear();
    }

    public static void broadcast() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> pending = true);
    }
}
