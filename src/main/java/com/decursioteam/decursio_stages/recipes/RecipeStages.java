package com.decursioteam.decursio_stages.recipes;

import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.restrictions.RecipeSelector;
import com.decursioteam.decursio_stages.utils.StageUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "decursio_stages")
public final class RecipeStages {
    private static final Map<AbstractContainerMenu, Set<ResourceLocation>> POLICIES = new WeakHashMap<>();

    /** Mutable snapshot retained for callers of the original API. */
    public static Set<ResourceLocation> blocked(ServerPlayer player) { return RestrictionsData.getRegistry().recipes().snapshot(player); }

    /** Internal read-only policy; unchanged derived ID sets are shared across refresh consumers. */
    public static Set<ResourceLocation> policy(ServerPlayer player) { return RestrictionsData.getRegistry().recipes().policy(player); }

    public static boolean blocked(ServerPlayer player, ResourceLocation id) { return RestrictionsData.getRegistry().recipes().blocked(player, id); }

    public static Optional<CraftingRecipe> select(ServerPlayer player, CraftingContainer input, Optional<CraftingRecipe> preferred) {
        Set<ResourceLocation> blocked = policy(player);
        if (preferred.isPresent() && !blocked.contains(preferred.get().getId())) return preferred;
        return player.server.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, player.level()).stream()
                .filter(recipe -> !blocked.contains(recipe.getId())).findFirst();
    }

    public static void refresh(RecipeBookMenu<?> menu) {
        if (menu instanceof InventoryMenu inv) inv.slotsChanged(inv.getCraftSlots());
        else if (menu instanceof CraftingMenu craft && !craft.slots.isEmpty()) craft.slotsChanged(craft.getSlot(1).container);
    }

    public static void refreshMenu(AbstractContainerMenu menu, ServerPlayer player) {
        if (menu instanceof CraftingMenu || menu instanceof InventoryMenu) refresh((RecipeBookMenu<?>) menu);
        else if (menu instanceof WorkstationRefresh workstation) workstation.decstages$refresh();
        menu.broadcastChanges();
    }

    public static List<String> explain(ServerPlayer player, ResourceLocation id) {
        List<String> lines = new ArrayList<>();
        var server = player.server;
        var recipe = server.getRecipeManager().byKey(id);
        lines.add("Recipe: " + id + " | " + (recipe.isEmpty() ? "UNKNOWN" : (ProcessorRecipes.supported(recipe.get().getType()) ? "Vanilla recipe execution supported; processors use " + CommonConfig.processorPolicy.get() : "Unsupported modded recipe type")));
        lines.add(blocked(player).contains(id) ? "BLOCKED for " + player.getName().getString() : "ALLOWED for " + player.getName().getString());
        var context = RecipeRestrictionIndex.displayContext(server);
        var resolved = recipe.orElse(null);
        RestrictionsData.getRegistry().getRestrictions().forEach((name, rule) -> {
            var data = rule.getData();
            var matchedSelectors = RecipeRestrictionIndex.matchingSelectors(data.getRecipeList(), id, resolved, context);
            var matchedExceptions = RecipeRestrictionIndex.matchingSelectors(data.getRecipeExceptionList(), id, resolved, context);
            if (!matchedSelectors.isEmpty() || !matchedExceptions.isEmpty()) {
                lines.add(name + ": selector=" + describeSelectors(matchedSelectors) + ", exception=" + describeSelectors(matchedExceptions)
                        + ", stage=" + data.getStage() + ", unlocked=" + StageUtil.hasStage(player, data.getStage())
                        + ", appliesToFakePlayers=" + rule.getSettingsCodec().getApplyToFakePlayers() + " [" + RestrictionsData.getRegistry().effectiveSource(name) + "]");
            }
        });
        lines.add("Every matching rule must be unlocked or excepted; item-use settings do not control recipe crafting.");
        return lines;
    }

    private static String describeSelectors(List<RecipeSelector> selectors) {
        if (selectors.isEmpty()) return "false";
        return selectors.stream().map(RecipeSelector::describe).collect(Collectors.joining(", "));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player && player.containerMenu != null) {
            var menu = player.containerMenu;
            if (!(menu instanceof CraftingMenu) && !(menu instanceof InventoryMenu) && !(menu instanceof WorkstationRefresh)) return;
            Set<ResourceLocation> current = policy(player);
            if (!current.equals(POLICIES.put(menu, current))) refreshMenu(menu, player);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RestrictionsData.getRegistry().recipes().forget(player);
            POLICIES.remove(player.containerMenu);
            POLICIES.remove(player.inventoryMenu);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        POLICIES.clear(); RestrictionsData.getRegistry().recipes().clearPlayers();
    }

    private RecipeStages() {}
}
