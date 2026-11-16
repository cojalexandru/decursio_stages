package com.decursioteam.decursio_stages.compat.plugins;

import com.decursioteam.decursio_stages.client.screens.RestrictEditorScreen;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.gui.screens.Screen;
import dev.architectury.fluid.FluidStack;
import java.util.stream.Stream;

import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.world.item.ItemStack;

import java.util.List;


@REIPluginClient
public class DecursioStagesREI implements REIClientPlugin {

    private long recipeRevision = -1;
    private BasicFilteringRule<?> filteringRule;
    private final com.decursioteam.decursio_stages.client.RecipeViewerUpdates<EntryStack<?>> updates;

    public DecursioStagesREI() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
            long revision = com.decursioteam.decursio_stages.client.ClientRecipeVisibility.revision();
            if (recipeRevision == revision || EntryRegistry.getInstance().isReloading()) return;
            recipeRevision = revision;
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null && minecraft.screen instanceof me.shedaniel.rei.api.client.gui.screen.DisplayScreen screen) {
                var context = me.shedaniel.rei.api.client.view.ViewSearchBuilder.builder();
                screen.getResultsToNotice().forEach(context::addRecipesFor);
                screen.getIngredientsToNotice().forEach(context::addUsagesFor);
                if (screen.getResultsToNotice().isEmpty() && screen.getIngredientsToNotice().isEmpty()) context.addAllCategories();
                context.setPreferredOpenedCategory(screen.getCurrentCategoryId());
                minecraft.setScreen(me.shedaniel.rei.api.client.REIRuntime.getInstance().getPreviousScreen());
                context.open();
            }
        });
        updates = new com.decursioteam.decursio_stages.client.RecipeViewerUpdates<>(
                () -> EntryRegistry.getInstance().getEntryStacks().toList(),
                entry -> entry.getType() == VanillaEntryTypes.ITEM ? (ItemStack) entry.getValue() : null,
                entry -> entry.getType() == VanillaEntryTypes.FLUID ? ((FluidStack) entry.getValue()).getFluid() : null,
                values -> filteringRule.show(values), values -> filteringRule.hide(values), () -> filteringRule != null && !EntryRegistry.getInstance().isReloading());
        updates.trackCatalogSize(() -> EntryRegistry.getInstance().size());
    }

    @Override
    public void registerBasicEntryFiltering(BasicFilteringRule<?> rule) {
        if (filteringRule == rule) updates.catalogChanged();
        else { filteringRule = rule; updates.runtimeChanged(); }
    }

    @Override
    public void registerDisplays(me.shedaniel.rei.api.client.registry.display.DisplayRegistry registry) {
        registry.registerVisibilityPredicate((category, display) -> {
            Object origin = registry.getDisplayOrigin(display);
            if (origin instanceof net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry
                    && com.decursioteam.decursio_stages.client.ClientRecipeVisibility.hiddenDisplay(entry.id().index()))
                return dev.architectury.event.EventResult.interruptFalse();
            var ids = display.provideInternalDisplayIds();
            if (ids.isEmpty()) ids = display.getDisplayLocation().map(java.util.List::of).orElse(java.util.List.of());
            return com.decursioteam.decursio_stages.recipes.RecipeVisibilityIndex.hidden(ids,
                    com.decursioteam.decursio_stages.client.ClientRecipeVisibility.blocked())
                    ? dev.architectury.event.EventResult.interruptFalse() : dev.architectury.event.EventResult.pass();
        });
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.exclusionZones().register(RestrictEditorScreen.class, screen -> {
            var b = screen.editorBounds();
            return List.of(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        });
        registry.registerDraggableStackVisitor(new DraggableStackVisitor<RestrictEditorScreen>() {
            @Override public <R extends Screen> boolean isHandingScreen(R screen) {
                return screen instanceof RestrictEditorScreen;
            }
            private boolean supported(EntryStack<?> stack) {
                return stack.getType() == VanillaEntryTypes.ITEM || stack.getType() == VanillaEntryTypes.FLUID;
            }
            @Override public Stream<BoundsProvider> getDraggableAcceptingBounds(
                    DraggingContext<RestrictEditorScreen> context,
                    DraggableStack dragged) {
                if (!supported(dragged.getStack())) return Stream.empty();
                var b = context.getScreen().dropBounds();
                return Stream.of(BoundsProvider.ofRectangle(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight())));
            }
            @Override public DraggedAcceptorResult acceptDraggedStack(
                    DraggingContext<RestrictEditorScreen> context,
                    DraggableStack dragged) {
                var screen = context.getScreen();
                var point = context.getCurrentPosition();
                var stack = dragged.getStack();
                boolean accepted = false;
                if (screen.acceptsDrop(point.x, point.y)) {
                    if (stack.getType() == VanillaEntryTypes.ITEM) accepted = screen.acceptItem((ItemStack) stack.getValue());
                    else if (stack.getType() == VanillaEntryTypes.FLUID) accepted = screen.acceptFluid(((FluidStack) stack.getValue()).getFluid());
                }
                return accepted ? DraggedAcceptorResult.ACCEPTED : DraggedAcceptorResult.PASS;
            }
        });
    }

}
