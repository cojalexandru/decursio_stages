package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.events.RestrictionsChangedEvent;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.utils.StageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.*;
import java.util.function.*;

/** Coalesces stage/rule notifications into one viewer update on the next client tick. */
public final class RecipeViewerUpdates<T> {
    private final Supplier<? extends Collection<T>> catalog;
    private final Function<T, ItemStack> item;
    private final Function<T, Fluid> fluid;
    private final Consumer<List<T>> show, hide;
    private final BooleanSupplier ready;
    private final Set<String> changed = new HashSet<>();
    private IngredientVisibility<T> visibility;
    private IntSupplier catalogSize;
    private int previousSize = -1;
    private boolean pending = true, rebuild = true, invalidateAll, stageDirty = true;

    public RecipeViewerUpdates(Supplier<? extends Collection<T>> catalog, Function<T, ItemStack> item, Function<T, Fluid> fluid,
                               Consumer<List<T>> show, Consumer<List<T>> hide, BooleanSupplier ready) {
        this.catalog = catalog; this.item = item; this.fluid = fluid; this.show = show; this.hide = hide; this.ready = ready;
        NeoForge.EVENT_BUS.addListener((SyncStagesEvent event) -> onClient(() -> { pending = true; stageDirty = true; }));
        NeoForge.EVENT_BUS.addListener((RestrictionsChangedEvent event) -> onClient(() -> { changed.addAll(event.changed()); pending = true; stageDirty |= event.full(); }));
        NeoForge.EVENT_BUS.addListener((RecipesUpdatedEvent event) -> onClient(() -> { rebuild = true; pending = true; }));
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> onClient(() -> { invalidateAll = true; pending = true; }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> onClient(() -> {
            if (visibility != null && ready.getAsBoolean()) visibility.restore();
            visibility = null; changed.clear(); pending = rebuild = true;
        }));
    }

    public void trackCatalogSize(IntSupplier size) { catalogSize = size; }
    private static void onClient(Runnable work) { Minecraft.getInstance().execute(work); }
    public void catalogChanged() { onClient(() -> pending = rebuild = true); }
    public void runtimeChanged() { onClient(() -> { visibility = null; previousSize = -1; pending = rebuild = true; }); }

    private void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null || !ready.getAsBoolean()) return;
        if (catalogSize != null) {
            int size = catalogSize.getAsInt();
            if (size != previousSize) { previousSize = size; pending = rebuild = true; }
        }
        if (!pending) return;
        var data = StageUtil.getPlayerData(player);
        if (data == null) return;
        if (visibility == null || rebuild) {
            if (visibility != null) visibility.restore();
            visibility = new IngredientVisibility<>(catalog.get(), item, fluid, show, hide);
            stageDirty = true;
        }
        visibility.update(RestrictionsData.getRegistry(), data::hasStage, Set.copyOf(changed), invalidateAll, stageDirty);
        changed.clear(); pending = rebuild = invalidateAll = stageDirty = false;
    }
}
