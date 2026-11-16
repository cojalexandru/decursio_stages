package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.StagesData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.persistence.StageFileStore;
import com.decursioteam.decursio_stages.persistence.StageSaveStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.*;
import java.util.*;

import static com.decursioteam.decursio_stages.DecursioStages.LOGGER;

@net.neoforged.fml.common.EventBusSubscriber(modid = "decursio_stages")
public class StagesHandler {
    private static final Map<UUID, IStagesData> GLOBAL_STAGE_DATA = new HashMap<>();
    private record Target(Path file, String name) {}
    private record Pending(Target target, CompoundTag snapshot) {}
    private static final Map<UUID, Target> TARGETS = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, CompoundTag> LAST_SAVED = new HashMap<>();
    private static final Set<UUID> LOGGING_OUT = new HashSet<>(), LOAD_BLOCKED = new HashSet<>(), RECOVERED = new HashSet<>();
    private static volatile IStagesData clientData;

    public static Set<String> getStages() {
        Set<String> stages = new HashSet<>();
        RestrictionsData.getRegistry().getRestrictions().values().forEach(rule -> stages.add(rule.getData().getStage()));
        return stages;
    }
    private static Target target(net.minecraft.world.entity.player.Player player, File directory, String uuid) {
        return new Target(directory.toPath().resolve("decursio_stages").resolve(uuid + ".dat").toAbsolutePath().normalize(), player.getName().getString());
    }

    @SubscribeEvent
    public static void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        UUID uuid = event.getEntity().getUUID();
        Target target = target(event.getEntity(), event.getPlayerDirectory(), event.getPlayerUUID());
        Target previousTarget = TARGETS.get(uuid);
        IStagesData previousData = GLOBAL_STAGE_DATA.get(uuid);
        if (previousData != null && previousTarget != null && previousTarget.file.equals(target.file) && PENDING.containsKey(uuid)) {
            try {
                CompoundTag latest = previousData.writeToNBT().copy();
                StageFileStore.validate(latest);
                PENDING.put(uuid, new Pending(target, latest));
            } catch (Exception error) {
                StageSaveStatus.failure(uuid, target.name, target.file, "Load deferred: cannot snapshot unsaved active stages. " + error.getMessage(), error);
                return;
            }
        }
        TARGETS.put(uuid, target); LOGGING_OUT.remove(uuid); LOAD_BLOCKED.remove(uuid); RECOVERED.remove(uuid); LAST_SAVED.remove(uuid);
        GLOBAL_STAGE_DATA.remove(uuid);
        try {
            Pending unsaved = PENDING.get(uuid);
            if (unsaved != null && !unsaved.target.file.equals(target.file)) throw new java.io.IOException("Unsaved stages belong to a different world path; resolve pending save first");
            var loaded = unsaved == null ? StageFileStore.load(target.file) : new StageFileStore.Loaded(unsaved.snapshot.copy(), false);
            var data = new StagesData();
            if (loaded.tag() != null) data.readFromNBT(loaded.tag());
            GLOBAL_STAGE_DATA.put(uuid, data);
            if (loaded.backup()) { RECOVERED.add(uuid); StageSaveStatus.recovered(uuid, target.name, target.file); }
            else if (unsaved == null) StageSaveStatus.success(uuid);
        } catch (Exception error) {
            // Never replace an unreadable history with a new empty stage file.
            LOAD_BLOCKED.add(uuid);
            StageSaveStatus.failure(uuid, target.name, target.file, "Load failed; saves blocked to protect existing files. Repair primary/backup and reconnect. " + error.getMessage(), error);
        }
    }

    private static boolean save(UUID uuid, Target target, IStagesData data) {
        CompoundTag snapshot;
        try {
            snapshot = data.writeToNBT().copy();
            StageFileStore.validate(snapshot);
        } catch (Exception error) {
            StageSaveStatus.failure(uuid, target.name, target.file, "Stage serialization failed: " + error.getMessage(), error);
            return false;
        }
        return saveSnapshot(uuid, target, snapshot);
    }
    private static boolean saveSnapshot(UUID uuid, Target target, CompoundTag snapshot) {
        try {
            StageFileStore.save(target.file, snapshot);
            PENDING.remove(uuid);
            if (GLOBAL_STAGE_DATA.containsKey(uuid)) LAST_SAVED.put(uuid, snapshot.copy());
            RECOVERED.remove(uuid); StageSaveStatus.success(uuid);
            return true;
        } catch (Exception error) {
            PENDING.put(uuid, new Pending(target, snapshot.copy()));
            StageSaveStatus.failure(uuid, target.name, target.file, "Save failed; last durable copies preserved. Snapshot queued for retry: " + error.getMessage(), error);
            return false;
        }
    }

    @SubscribeEvent
    public static void onPlayerSave(PlayerEvent.SaveToFile event) {
        UUID uuid = event.getEntity().getUUID();
        if (LOAD_BLOCKED.contains(uuid)) return;
        IStagesData data = GLOBAL_STAGE_DATA.get(uuid);
        if (data == null) return;
        Target target = target(event.getEntity(), event.getPlayerDirectory(), event.getPlayerUUID());
        TARGETS.put(uuid, target);
        save(uuid, target, data);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // NeoForge fires logout BEFORE vanilla's final SaveToFile event.
        // Keep the data through all logout/save listeners and evict at the end of the tick.
        LOGGING_OUT.add(event.getEntity().getUUID());
    }
    private static void finishLogout(UUID uuid) {
        IStagesData data = GLOBAL_STAGE_DATA.get(uuid);
        Target target = TARGETS.get(uuid);
        if (data != null && target != null) {
            CompoundTag snapshot;
            try { snapshot = data.writeToNBT().copy(); StageFileStore.validate(snapshot); }
            catch (Exception error) { save(uuid, target, data); return; }
            CompoundTag last = LAST_SAVED.get(uuid);
            boolean justSaved = !PENDING.containsKey(uuid) && snapshot.equals(last) && Files.isRegularFile(target.file);
            if (!justSaved) saveSnapshot(uuid, target, snapshot);
        }
        GLOBAL_STAGE_DATA.remove(uuid); TARGETS.remove(uuid); LAST_SAVED.remove(uuid);
        LOAD_BLOCKED.remove(uuid); RECOVERED.remove(uuid); LOGGING_OUT.remove(uuid);
    }
    private static void retryPending() {
        for (var entry : new ArrayList<>(PENDING.entrySet())) {
            UUID uuid = entry.getKey();
            if (LOAD_BLOCKED.contains(uuid)) continue;
            IStagesData active = GLOBAL_STAGE_DATA.get(uuid);
            if (active != null) save(uuid, entry.getValue().target, active);
            else saveSnapshot(uuid, entry.getValue().target, entry.getValue().snapshot);
        }
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (UUID uuid : new ArrayList<>(LOGGING_OUT)) finishLogout(uuid);
        if (event.getServer().getTickCount() % 200 == 0) retryPending();
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        try {
            for (var entry : new ArrayList<>(GLOBAL_STAGE_DATA.entrySet())) {
                Target target = TARGETS.get(entry.getKey());
                if (target != null && !LOAD_BLOCKED.contains(entry.getKey())) save(entry.getKey(), target, entry.getValue());
            }
            retryPending();
            PENDING.forEach((uuid, pending) -> LOGGER.error("UNSAVED STAGES at shutdown for {} ({}), {}. Last durable copies retained. Recovery snapshot: {}",
                    pending.target.name, uuid, pending.target.file, pending.snapshot));
        } finally {
            GLOBAL_STAGE_DATA.clear(); TARGETS.clear(); LAST_SAVED.clear(); PENDING.clear();
            LOGGING_OUT.clear(); LOAD_BLOCKED.clear(); RECOVERED.clear(); clientData = null; StageSaveStatus.clear();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        if (LOAD_BLOCKED.contains(uuid)) {
            // The client gets an empty read-only view; no empty server data is installed or saved.
            com.decursioteam.decursio_stages.DecursioStages.NETWORK.sendToPlayer(player,
                    new com.decursioteam.decursio_stages.network.messages.SyncStagesMessage(List.of()));
            player.sendSystemMessage(Component.literal("Your stages could not be loaded. Saves are paused to protect your data; contact an operator (/decstages diagnose saves).").withStyle(ChatFormatting.RED));
        } else {
            StageUtil.syncPlayer(player);
            if (RECOVERED.contains(uuid)) player.sendSystemMessage(Component.literal("Your stages were recovered from a backup. An operator can check /decstages diagnose saves.").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static List<String> persistenceReport() {
        List<String> lines = new ArrayList<>();
        lines.add("Stage storage: " + GLOBAL_STAGE_DATA.size() + " loaded players, " + PENDING.size() + " queued saves, " + LOAD_BLOCKED.size() + " protected load failures.");
        lines.add("Pending snapshots retry every 200 server ticks and at shutdown.");
        if (!PENDING.isEmpty()) lines.add("Repair file access before stopping the server so queued progress can become durable.");
        PENDING.forEach((uuid, pending) -> lines.add("WARN pending: " + pending.target.name + " (" + uuid + ") [" + pending.target.file + "]"));
        lines.addAll(StageSaveStatus.report(null));
        return lines;
    }
    @Nullable public static IStagesData getPlayerData(UUID uuid) { return GLOBAL_STAGE_DATA.get(uuid); }
    @Nullable public static IStagesData getClientData() { return clientData; }
    public static void setClientData(IStagesData stageData) { clientData = stageData; }
    public static void clearClientData() { clientData = null; }
}
