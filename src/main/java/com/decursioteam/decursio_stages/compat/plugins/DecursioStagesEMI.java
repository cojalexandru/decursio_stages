package com.decursioteam.decursio_stages.compat.plugins;

import com.decursioteam.decursio_stages.client.ClientRecipeVisibility;
import com.decursioteam.decursio_stages.client.screens.RestrictEditorScreen;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.events.RestrictionsChangedEvent;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.recipes.RecipeVisibilityIndex;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.decursioteam.decursio_stages.utils.StageUtil;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EmiEntrypoint
public class DecursioStagesEMI implements EmiPlugin {

    private long recipeRevision = -1;
    private boolean reloadPending;

    public DecursioStagesEMI() {
        if (EffectiveSide.get().isClient()) {
            NeoForge.EVENT_BUS.addListener((SyncStagesEvent event) -> onClient(() -> reloadPending = true));
            NeoForge.EVENT_BUS.addListener((RestrictionsChangedEvent event) -> onClient(() -> reloadPending = true));
            NeoForge.EVENT_BUS.addListener((RecipesUpdatedEvent event) -> onClient(() -> reloadPending = true));
            NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> onClient(() -> reloadPending = true));
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> onClient(() -> {
                reloadPending = false;
                recipeRevision = -1;
            }));
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        }
    }

    private static void onClient(Runnable work) {
        Minecraft.getInstance().execute(work);
    }

    private void tick() {
        long revision = ClientRecipeVisibility.revision();
        if (recipeRevision != revision) {
            recipeRevision = revision;
            reloadPending = true;
        }
        var mc = Minecraft.getInstance();
        if (!reloadPending || mc.level == null || mc.player == null) return;
        reloadPending = false;
        EmiReloadManager.reload();
    }

    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(RestrictEditorScreen.class, (screen, consumer) -> {
            var b = screen.editorBounds();
            consumer.accept(new Bounds(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        });
        registry.addDragDropHandler(RestrictEditorScreen.class, (screen, ingredient, x, y) -> {
            if (!screen.acceptsDrop(x, y) || ingredient.getEmiStacks().size() != 1) return false;
            EmiStack stack = ingredient.getEmiStacks().get(0);
            if (stack.getKey() instanceof Fluid fluid) return screen.acceptFluid(fluid);
            return screen.acceptItem(stack.getItemStack());
        });

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var stageData = StageUtil.getPlayerData(player);
        if (stageData == null) return;

        var restrictions = RestrictionsData.getRegistry();
        List<String> hiddenRules = new ArrayList<>();
        restrictions.getRestrictions().forEach((id, data) -> {
            if (data.getSettingsCodec().getHideInJEI() && !stageData.hasStage(data.getData().getStage())) {
                hiddenRules.add(id);
            }
        });

        if (!hiddenRules.isEmpty()) {
            registry.removeEmiStacks(emiStack -> {
                if (emiStack.getKey() instanceof Fluid fluid) {
                    for (String id : hiddenRules) {
                        if (ResourceUtil.isFluidRestricted(id, fluid)) return true;
                    }
                    return false;
                }
                ItemStack stack = emiStack.getItemStack();
                if (stack.isEmpty()) return false;
                for (String id : hiddenRules) {
                    if (restrictions.compiled(id).matches(stack, ResourceUtil.CHECK_TYPES.ALL)) return true;
                }
                return false;
            });
        }

        Set<ResourceLocation> blockedRecipes = ClientRecipeVisibility.blocked();
        if (!blockedRecipes.isEmpty()) {
            registry.removeRecipes(recipe -> {
                Set<ResourceLocation> ids = new HashSet<>();
                if (recipe.getId() != null) ids.add(recipe.getId());
                if (recipe.getBackingRecipe() != null) ids.add(recipe.getBackingRecipe().id());
                return RecipeVisibilityIndex.hidden(ids, blockedRecipes);
            });
        }
    }
}