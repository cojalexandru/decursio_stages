package com.decursioteam.decursio_stages.persistence;

import com.decursioteam.decursio_stages.DecursioStages;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Bounded diagnostic history; stage snapshots awaiting retry live separately in StagesHandler. */
public final class StageSaveStatus {
    private record Issue(String name, Path file, String message, Instant attempted, boolean resolved, long notified) {}
    private static final Map<UUID, Issue> ISSUES = new LinkedHashMap<>();
    private StageSaveStatus() {}

    public static void failure(UUID uuid, String name, Path file, String message, Throwable error) {
        Issue previous = ISSUES.get(uuid);
        long now = System.nanoTime();
        boolean notify = previous == null || previous.resolved || previous.message.startsWith("Loaded the backup") || previous.message.startsWith("Load") != message.startsWith("Load") || now - previous.notified >= 60_000_000_000L;
        put(uuid, new Issue(name, file, message, Instant.now(), false, notify ? now : previous.notified));
        if (!notify) return;
        DecursioStages.LOGGER.error("Stage persistence failed for {} ({}), {}: {}", name, uuid, file, message, error);
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (var player : server.getPlayerList().getPlayers()) {
            if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack()))
                player.sendSystemMessage(Component.literal("Could not persist/load stages for " + name + ". Use /decstages diagnose saves for details.").withStyle(ChatFormatting.RED));
        }
    }
    public static void recovered(UUID uuid, String name, Path file) {
        put(uuid, new Issue(name, file, "Loaded the backup; primary will be repaired on save.", Instant.now(), false, System.nanoTime()));
        DecursioStages.LOGGER.warn("Recovered stages for {} ({}) from {}", name, uuid, StageFileStore.backup(file));
    }
    public static void success(UUID uuid) {
        Issue issue = ISSUES.get(uuid);
        if (issue != null && !issue.resolved) put(uuid, new Issue(issue.name, issue.file, issue.message, Instant.now(), true, issue.notified));
    }
    private static void put(UUID uuid, Issue issue) {
        ISSUES.remove(uuid);
        if (ISSUES.size() >= 128) ISSUES.remove(ISSUES.keySet().iterator().next());
        ISSUES.put(uuid, issue);
    }
    public static List<String> report(UUID only) {
        List<String> lines = new ArrayList<>();
        ISSUES.forEach((uuid, issue) -> {
            if (only == null || only.equals(uuid)) lines.add((issue.resolved ? "Resolved: " : "WARN persistence: ") + issue.name + " (" + uuid + ") at " + issue.attempted + ": " + issue.message + " [" + issue.file + "]");
        });
        if (lines.isEmpty()) lines.add("No recorded stage persistence problems in this server session.");
        return lines;
    }
    public static void clear() { ISSUES.clear(); }
}
