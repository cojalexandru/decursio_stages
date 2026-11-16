package com.decursioteam.decursio_stages.compat.plugins;

import com.decursioteam.decursio_stages.client.ClientRecipeVisibility;
import com.decursioteam.decursio_stages.client.RecipeScreenRefresh;
import com.decursioteam.decursio_stages.client.RecipeViewerUpdates;
import com.decursioteam.decursio_stages.client.screens.RestrictEditorScreen;
import com.decursioteam.decursio_stages.recipes.RecipeVisibilityIndex;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.decursioteam.decursio_stages.DecursioStages.MOD_ID;

@JeiPlugin
public class DecursioStagesJEI implements IModPlugin {

    private record RecipeEntry<T>(IRecipeCategory<T> category, T recipe) {}
    private RecipeVisibilityIndex<RecipeEntry<?>> recipeIndex;
    private long recipeRevision = -1;
    private long catalogRevision = -1;
    private IJeiRuntime jeiRuntime;
    private boolean changingVisibility;
    private final RecipeViewerUpdates<Object> updates;

    public DecursioStagesJEI() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> updateRecipes());
        updates = new RecipeViewerUpdates<>(() -> {
            List<Object> catalog = new ArrayList<>(jeiRuntime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK));
            catalog.addAll(jeiRuntime.getIngredientManager().getAllIngredients(NeoForgeTypes.FLUID_STACK));
            return catalog;
        }, value -> value instanceof ItemStack stack ? stack : null,
           value -> value instanceof FluidStack stack ? stack.getFluid() : null,
           values -> changeVisibility(values, false), values -> changeVisibility(values, true), () -> jeiRuntime != null);
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
        if (recipeIndex != null && jeiRuntime == runtime) {
            recipeIndex.update(Set.of(), ignored -> {}, values -> values.forEach(value -> recipeVisibility(value, false)));
        }
        recipeIndex = null;
        recipeRevision = -1;
        if (jeiRuntime == runtime) updates.catalogChanged();
        else {
            jeiRuntime = runtime;
            updates.runtimeChanged();
            runtime.getIngredientManager().registerIngredientListener(new IIngredientManager.IIngredientListener() {
                private void changed() {
                    if (!changingVisibility && jeiRuntime == runtime) {
                        updates.catalogChanged();
                        catalogRevision = -1;
                    }
                }
                @Override public <V> void onIngredientsAdded(IIngredientHelper<V> helper, Collection<ITypedIngredient<V>> values) { changed(); }
                @Override public <V> void onIngredientsRemoved(IIngredientHelper<V> helper, Collection<ITypedIngredient<V>> values) { changed(); }
                @Override public <V> void onIngredientsUpdated(IIngredientHelper<V> helper, Collection<ITypedIngredient<V>> values) { changed(); }
            });
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        recipeIndex = null;
        jeiRuntime = null;
        updates.runtimeChanged();
    }

    private <T> void collectRecipes(IRecipeCategory<T> category, List<RecipeVisibilityIndex.Entry<RecipeEntry<?>>> entries) {
        jeiRuntime.getRecipeManager().createRecipeLookup(category.getRecipeType()).get().forEach(recipe -> {
            var ids = recipe instanceof RecipeHolder<?> holder
                    ? Set.of(holder.id()) : Set.copyOf(category.getSourceRecipeIds(recipe));
            if (!ids.isEmpty()) entries.add(new RecipeVisibilityIndex.Entry<>(new RecipeEntry<>(category, recipe), ids));
        });
    }

    private <T> void recipeVisibility(RecipeEntry<T> entry, boolean hide) {
        if (hide) jeiRuntime.getRecipeManager().hideRecipes(entry.category.getRecipeType(), List.of(entry.recipe));
        else jeiRuntime.getRecipeManager().unhideRecipes(entry.category.getRecipeType(), List.of(entry.recipe));
    }

    private void updateRecipes() {
        if (jeiRuntime == null) return;
        var stateRevision = ClientRecipeVisibility.revision();
        if (recipeIndex == null || catalogRevision != ClientRecipeVisibility.catalogRevision()) {
            if (recipeIndex != null) {
                recipeIndex.update(Set.of(), ignored -> {}, values -> values.forEach(value -> recipeVisibility(value, false)));
            }
            var entries = new ArrayList<RecipeVisibilityIndex.Entry<RecipeEntry<?>>>();
            jeiRuntime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().forEach(category -> collectRecipes(category, entries));
            recipeIndex = new RecipeVisibilityIndex<>(entries);
            catalogRevision = ClientRecipeVisibility.catalogRevision();
            recipeRevision = -1;
        }
        if (recipeRevision == stateRevision) return;
        recipeIndex.update(ClientRecipeVisibility.blocked(),
                values -> values.forEach(value -> recipeVisibility(value, true)),
                values -> values.forEach(value -> recipeVisibility(value, false)));
        recipeRevision = stateRevision;
        if (Minecraft.getInstance().screen instanceof RecipeScreenRefresh screen) screen.decstages$refreshRecipes();
    }

    private void changeVisibility(List<Object> values, boolean hide) {
        changingVisibility = true;
        try {
            var manager = jeiRuntime.getIngredientManager();
            var items = values.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast).toList();
            var fluids = values.stream().filter(FluidStack.class::isInstance).map(FluidStack.class::cast).toList();
            if (!items.isEmpty()) {
                if (hide) manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, items);
                else manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, items);
            }
            if (!fluids.isEmpty()) {
                if (hide) manager.removeIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluids);
                else manager.addIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluids);
            }
        } finally {
            changingVisibility = false;
            // Recipes omitted because their ingredients were hidden must be reconsidered after an item-stage change.
            catalogRevision = -1;
        }
    }

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, "main");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(RestrictEditorScreen.class, new IGhostIngredientHandler<>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(RestrictEditorScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
                if (!(ingredient.getIngredient() instanceof ItemStack) && !(ingredient.getIngredient() instanceof FluidStack)) {
                    return List.of();
                }
                return List.of(new Target<>() {
                    @Override public Rect2i getArea() { return screen.dropBounds(); }
                    @Override public void accept(I value) {
                        if (value instanceof ItemStack stack) screen.acceptItem(stack);
                        else if (value instanceof FluidStack fluid) screen.acceptFluid(fluid.getFluid());
                    }
                });
            }
            @Override public void onComplete() {}
        });
    }
}