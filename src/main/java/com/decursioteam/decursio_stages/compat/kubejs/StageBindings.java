package com.decursioteam.decursio_stages.compat.kubejs;

import com.decursioteam.decursio_stages.utils.StageUtil;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Locale;

/** Server-script binding. Uses the same events, persistence and sync as commands. */
public final class StageBindings {
    private StageBindings() {}

    public static String normalizeStage(String stage) {
        if (stage == null || stage.isBlank() || stage.length() > 64)
            throw new IllegalArgumentException("Stage must contain 1-64 characters");
        return stage.toLowerCase(Locale.ROOT);
    }

    private static void requireServerThread(ServerPlayer player) {
        if (player == null || player.level().getServer() == null || !player.level().getServer().isSameThread())
            throw new IllegalStateException("Stage methods require a server player on the server thread");
    }

    public static boolean has(ServerPlayer player, String stage) {
        requireServerThread(player);
        return StageUtil.hasStage(player, normalizeStage(stage));
    }

    public static boolean add(ServerPlayer player, String stage) {
        requireServerThread(player);
        stage = normalizeStage(stage);
        var data = StageUtil.getPlayerData(player);
        if (data == null || data.hasStage(stage)) return false;
        StageUtil.addStage(player, stage);
        return data.hasStage(stage);
    }

    public static boolean remove(ServerPlayer player, String stage) {
        requireServerThread(player);
        stage = normalizeStage(stage);
        var data = StageUtil.getPlayerData(player);
        if (data == null || !data.hasStage(stage)) return false;
        StageUtil.removeStage(player, stage);
        return !data.hasStage(stage);
    }

    public static int clear(ServerPlayer player) {
        requireServerThread(player);
        return StageUtil.clearStages(player);
    }

    public static List<String> getStages(ServerPlayer player) {
        requireServerThread(player);
        var data = StageUtil.getPlayerData(player);
        return data == null ? List.of() : data.getStages().stream().sorted().toList();
    }
}
