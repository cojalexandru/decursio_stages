package com.decursioteam.decursio_stages.compat.kubejs;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.ScriptRestrictions;
import com.decursioteam.decursio_stages.events.UpdateStageEvent;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;

public final class DecursioStagesKubeJSPlugin extends KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("DecursioStagesEvents");
    public static final EventHandler RESTRICTIONS = GROUP.server("restrictions", () -> RestrictionsEventJS.class);
    public static final EventHandler ADDING = GROUP.server("stageAdding", () -> StageEventJS.class).hasResult();
    public static final EventHandler ADDED = GROUP.server("stageAdded", () -> StageEventJS.class);
    public static final EventHandler REMOVING = GROUP.server("stageRemoving", () -> StageEventJS.class).hasResult();
    public static final EventHandler REMOVED = GROUP.server("stageRemoved", () -> StageEventJS.class);
    public static final EventHandler CLEARED = GROUP.server("stagesCleared", () -> StageEventJS.class);

    @Override public void registerEvents() { GROUP.register(); }
    @Override public void registerBindings(BindingsEvent event) {
        if (event.getType() == ScriptType.SERVER) event.add("DecursioStages", StageBindings.class);
    }
    @Override public void init() {
        MinecraftForge.EVENT_BUS.addListener(this::adding);
        MinecraftForge.EVENT_BUS.addListener(this::added);
        MinecraftForge.EVENT_BUS.addListener(this::removing);
        MinecraftForge.EVENT_BUS.addListener(this::removed);
        MinecraftForge.EVENT_BUS.addListener(this::cleared);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }
    @Override public void onServerReload() {
        var event = new RestrictionsEventJS();
        boolean[] failed = {false};
        RESTRICTIONS.post(ScriptType.SERVER, event, (e, handler, error) -> {
            failed[0] = true;
            return error;
        });
        if (failed[0]) {
            DecursioStages.LOGGER.error("Keeping previous script restrictions because a KubeJS handler failed");
            return;
        }
        try {
            ScriptRestrictions.replace(event.validate());
        } catch (RuntimeException error) {
            ScriptType.SERVER.console.error("Keeping previous Decursio Stages script restrictions", error);
        }
    }
    private void adding(UpdateStageEvent.Add event) {
        if (event.getEntity() instanceof ServerPlayer player &&
                ADDING.post(new StageEventJS(player, event.getStageName())).interruptFalse()) event.setCanceled(true);
    }
    private void added(UpdateStageEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player) ADDED.post(new StageEventJS(player, event.getStageName()));
    }
    private void removing(UpdateStageEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player &&
                REMOVING.post(new StageEventJS(player, event.getStageName())).interruptFalse()) event.setCanceled(true);
    }
    private void removed(UpdateStageEvent.Removed event) {
        if (event.getEntity() instanceof ServerPlayer player) REMOVED.post(new StageEventJS(player, event.getStageName()));
    }
    private void cleared(UpdateStageEvent.Cleared event) {
        if (event.getEntity() instanceof ServerPlayer player) CLEARED.post(new StageEventJS(player, ""));
    }
    private void stopped(ServerStoppedEvent event) { ScriptRestrictions.clear(); }
}
