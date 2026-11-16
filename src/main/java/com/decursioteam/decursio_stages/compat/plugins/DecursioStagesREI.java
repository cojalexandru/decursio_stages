package com.decursioteam.decursio_stages.compat.plugins;

import com.decursioteam.decursio_stages.client.ClientRecipeVisibility;
import com.decursioteam.decursio_stages.client.RecipeViewerUpdates;
import com.decursioteam.decursio_stages.client.screens.RestrictEditorScreen;
import com.decursioteam.decursio_stages.recipes.RecipeVisibilityIndex;
import dev.architectury.event.EventResult;
import dev.architectury.fluid.FluidStack;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.gui.screen.DisplayScreen;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.stream.Stream;

@REIPluginClient
public class DecursioStagesREI implements REIClientPlugin {

    private long recipeRevision = -1;
    private BasicFilteringRule<?> filteringRule;
    private final RecipeViewerUpdates<EntryStack<?>> updates;

    public DecursioStagesREI() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            long revision = ClientRecipeVisibility.revision();
            if (recipeRevision == revision || EntryRegistry.getInstance().isReloading()) return;
            recipeRevision = revision;
            var minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.screen instanceof DisplayScreen screen) {
                var context = ViewSearchBuilder.builder();
                screen.getResultsToNotice().forEach(context::addRecipesFor);
                screen.getIngredientsToNotice().forEach(context::addUsagesFor);
                if (screen.getResultsToNotice().isEmpty() && screen.getIngredientsToNotice().isEmpty()) context.addAllCategories();
                context.setPreferredOpenedCategory(screen.getCurrentCategoryId());
                minecraft.setScreen(REIRuntime.getInstance().getPreviousScreen());
                context.open();
            }
        });
        updates = new RecipeViewerUpdates<>(
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
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerVisibilityPredicate((category, display) -> {
            Object origin = registry.getDisplayOrigin(display);
            var ids = display.provideInternalDisplayIds();
            if (ids.isEmpty()) ids = display.getDisplayLocation().map(List::of).orElse(List.of());
            if (ids.isEmpty() && origin instanceof Recipe<?> recipe) ids = List.of(recipe.getId());
            return RecipeVisibilityIndex.hidden(ids, ClientRecipeVisibility.blocked())
                    ? EventResult.interruptFalse() : EventResult.pass();
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
            @Override public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<RestrictEditorScreen> context, DraggableStack dragged) {
                if (!supported(dragged.getStack())) return Stream.empty();
                var b = context.getScreen().dropBounds();
                return Stream.of(BoundsProvider.ofRectangle(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight())));
            }
            @Override public DraggedAcceptorResult acceptDraggedStack(DraggingContext<RestrictEditorScreen> context, DraggableStack dragged) {
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