package com.decursioteam.decursio_stages.recipes;

import com.decursioteam.decursio_stages.datagen.*;
import com.decursioteam.decursio_stages.utils.StageUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

@EventBusSubscriber(modid = "decursio_stages")
public final class RecipeStages {
    private static final Map<net.minecraft.world.inventory.AbstractContainerMenu, Set<Identifier>> POLICIES = new WeakHashMap<>();
    /** Mutable snapshot retained for callers of the original API. */
    public static Set<Identifier> blocked(ServerPlayer player) { return RestrictionsData.getRegistry().recipes().snapshot(player); }
    /** Internal read-only policy; unchanged derived ID sets are shared across refresh consumers. */
    public static Set<Identifier> policy(ServerPlayer player) { return RestrictionsData.getRegistry().recipes().policy(player); }
    public static boolean blocked(ServerPlayer player, Identifier id) { return RestrictionsData.getRegistry().recipes().blocked(player, id); }
    public static Optional<RecipeHolder<CraftingRecipe>> select(ServerPlayer player, CraftingInput input, Optional<RecipeHolder<CraftingRecipe>> preferred) {
        Set<Identifier> blocked = policy(player);
        if (preferred.isPresent() && !blocked.contains(preferred.get().id().identifier())) return preferred;
        return player.level().getServer().getRecipeManager().recipeMap().getRecipesFor(RecipeType.CRAFTING, input, player.level())
                .filter(recipe -> !blocked.contains(recipe.id().identifier())).findFirst();
    }
    public static void refresh(AbstractCraftingMenu menu) { menu.slotsChanged(menu.getInputGridSlots().getFirst().container); }
    public static void refreshMenu(net.minecraft.world.inventory.AbstractContainerMenu menu, ServerPlayer player) {
        if (menu instanceof AbstractCraftingMenu craft) refresh(craft);
        else if (menu instanceof WorkstationRefresh workstation) workstation.decstages$refresh();
        menu.broadcastChanges();
    }
    public static List<String> explain(ServerPlayer player, Identifier id) {
        List<String> lines = new ArrayList<>();
        var server = player.level().getServer();
        var recipe = server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id));
        lines.add("Recipe: " + id + " | " + (recipe.isEmpty() ? "UNKNOWN" : (ProcessorRecipes.supported(recipe.get().value().getType()) ? "Vanilla recipe execution supported; processors use " + com.decursioteam.decursio_stages.config.CommonConfig.processorPolicy.get() : "Unsupported modded recipe type")));
        lines.add(blocked(player).contains(id) ? "BLOCKED for " + player.getName().getString() : "ALLOWED for " + player.getName().getString());
        var context = RecipeRestrictionIndex.displayContext(server);
        var holder = recipe.orElse(null);
        RestrictionsData.getRegistry().getRestrictions().forEach((name, rule) -> {
            var data = rule.getData();
            var matchedSelectors = RecipeRestrictionIndex.matchingSelectors(data.getRecipeList(), id, holder, context);
            var matchedExceptions = RecipeRestrictionIndex.matchingSelectors(data.getRecipeExceptionList(), id, holder, context);
            if (!matchedSelectors.isEmpty() || !matchedExceptions.isEmpty())
                lines.add(name + ": selector=" + describeSelectors(matchedSelectors) + ", exception=" + describeSelectors(matchedExceptions)
                        + ", stage=" + data.getStage() + ", unlocked=" + StageUtil.hasStage(player, data.getStage())
                        + ", appliesToFakePlayers=" + rule.getSettingsCodec().getApplyToFakePlayers() + " [" + RestrictionsData.getRegistry().effectiveSource(name) + "]");
        });
        lines.add("Every matching rule must be unlocked or excepted; item-use settings do not control recipe crafting.");
        return lines;
    }

    private static String describeSelectors(List<com.decursioteam.decursio_stages.restrictions.RecipeSelector> selectors) {
        if (selectors.isEmpty()) return "false";
        return selectors.stream().map(com.decursioteam.decursio_stages.restrictions.RecipeSelector::describe).collect(java.util.stream.Collectors.joining(", "));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu != null) {
            var menu = player.containerMenu;
            // A crafter's responsible player may differ from its viewer. Re-evaluate that owner's
            // effective stages every tick, without building the viewer's unrelated recipe policy.
            if (menu instanceof net.minecraft.world.inventory.CrafterMenu) { refreshMenu(menu, player); return; }
            if (!(menu instanceof AbstractCraftingMenu) && !(menu instanceof WorkstationRefresh)) return;
            Set<Identifier> current = policy(player);
            if (!current.equals(POLICIES.put(menu, current))) refreshMenu(menu, player);
        }
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RestrictionsData.getRegistry().recipes().forget(player);
            POLICIES.remove(player.containerMenu);
            POLICIES.remove(player.inventoryMenu);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        POLICIES.clear(); RestrictionsData.getRegistry().recipes().clearPlayers();
    }
    private RecipeStages() {}
}
