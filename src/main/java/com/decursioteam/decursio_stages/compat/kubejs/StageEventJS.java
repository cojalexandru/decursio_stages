package com.decursioteam.decursio_stages.compat.kubejs;

import dev.latvian.mods.kubejs.player.PlayerEventJS;
import net.minecraft.server.level.ServerPlayer;

public final class StageEventJS extends PlayerEventJS {
    private final ServerPlayer player;
    private final String stage;

    public StageEventJS(ServerPlayer player, String stage) {
        this.player = player;
        this.stage = stage;
    }
    @Override public ServerPlayer getEntity() { return player; }
    /** Empty for stagesCleared, which represents a bulk operation. */
    public String getStage() { return stage; }
}
