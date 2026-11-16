package com.decursioteam.decursio_stages.diagnostics;

import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.decursioteam.decursio_stages.recipes.RecipeStages;
import com.decursioteam.decursio_stages.utils.StageUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.*;

/** Read-only machine snapshots; recipe permission is separate from fuel/redstone/output capacity. */
public final class ProcessorInspector {
    private ProcessorInspector() {}

    public static List<String> inspect(BlockEntity entity, Identifier requested, ServerPlayer collector) {
        if (!ProcessorRecipes.processor(entity)) throw new IllegalArgumentException("Not a supported recipe processor");
        var level = (ServerLevel) entity.getLevel();
        var policy = CommonConfig.processorPolicy.get();
        boolean outputOnly = policy == CommonConfig.ProcessorPolicy.OUTPUT_ONLY;
        List<String> lines = new ArrayList<>();
        lines.add("Machine: " + entity.getBlockState().getBlock() + " at " + entity.getBlockPos().toShortString()
                + " in " + level.dimension().identifier());
        lines.add("Policy: " + policy + " | " + (outputOnly ? "Collector stages control collection; processing is unrestricted."
                : (policy == CommonConfig.ProcessorPolicy.PLACER ? "Placer" : "Last ingredient inserter") + " stages control processing."));
        lines.add(stored(entity, true));
        lines.add(stored(entity, false));
        var access = outputOnly ? null : ProcessorRecipes.responsibility(entity, policy == CommonConfig.ProcessorPolicy.PLACER);
        if (access != null) {
            lines.add("Controlling player: " + (access.id() == null ? "unknown" : access.online() == null ? access.id() : access.online().getName().getString() + " (" + access.id() + ")")
                    + " | fake=" + access.fake());
            lines.add(access.status());
            if (access.online() == null) lines.add("Offline stages use durable storage; player-context stage-check events do not run.");
        } else lines.add("Collector: " + (collector == null ? "unspecified; add 'player <player>' to diagnose collection" : collector.getName().getString()));

        var candidates = candidates(entity);
        if (requested == null && candidates.isEmpty()) lines.add("No recipe matches the current inputs. Use 'recipe <id>' to inspect a specific recipe.");
        Set<Identifier> ids = new LinkedHashSet<>();
        if (requested != null) ids.add(requested);
        else candidates.forEach(recipe -> ids.add(recipe.id().identifier()));
        var context = com.decursioteam.decursio_stages.recipes.RecipeRestrictionIndex.displayContext(level.getServer());
        for (var id : ids) {
            var recipe = level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id));
            if (recipe.isEmpty()) { lines.add("ERROR unknown recipe: " + id); continue; }
            boolean compatible = recipe.get().value().getType() == type(entity);
            lines.add("Recipe: " + id + " | compatible type=" + compatible + " | matches inputs="
                    + candidates.stream().anyMatch(candidate -> candidate.id().identifier().equals(id)));
            if (!compatible) { lines.add("Not executed by this processor type."); continue; }
            boolean blocked = false;
            var holder = recipe.get();
            for (var entry : RestrictionsData.getRegistry().getRestrictions().entrySet()) {
                var rule = entry.getValue();
                var data = rule.getData();
                boolean selector = com.decursioteam.decursio_stages.recipes.RecipeRestrictionIndex.matchesAny(data.getRecipeList(), id, holder, context);
                boolean exception = com.decursioteam.decursio_stages.recipes.RecipeRestrictionIndex.matchesAny(data.getRecipeExceptionList(), id, holder, context);
                if (!selector && !exception) continue;
                boolean fake = outputOnly ? collector instanceof FakePlayer : access.fake();
                boolean applies = !fake || rule.getSettingsCodec().getApplyToFakePlayers();
                Boolean unlocked = null;
                if (!outputOnly) unlocked = access.unlocked(data.getStage());
                else if (collector != null) unlocked = StageUtil.hasStage(collector, data.getStage());
                boolean denies = selector && !exception && applies && Boolean.FALSE.equals(unlocked);
                blocked |= denies;
                lines.add(entry.getKey() + ": " + (denies ? "BLOCKED" : unlocked == null && selector && !exception && applies ? "collector required" : "does not block")
                        + " | selector=" + selector + ", exception=" + exception + ", stage=" + data.getStage() + ", unlocked=" + (unlocked == null ? "not evaluated" : unlocked)
                        + ", appliesToFakePlayers=" + rule.getSettingsCodec().getApplyToFakePlayers()
                        + " [" + RestrictionsData.getRegistry().effectiveSource(entry.getKey()) + "]");
            }
            lines.add((outputOnly ? "Processing: ALLOWED | collection for this recipe: " : "Processing: ")
                    + (outputOnly && collector == null ? "not evaluated (collector required)" : blocked ? "BLOCKED" : "ALLOWED"));
        }
        if (outputOnly && entity instanceof AbstractFurnaceBlockEntity furnace) {
            String receipts = ProcessorRecipes.furnaceReceipts(furnace);
            lines.add("Current output recipe provenance: " + (receipts.isEmpty() ? "none" : receipts));
            if (collector != null && !furnace.getItem(2).isEmpty()) {
                lines.add("Current output collection: " + (ProcessorRecipes.mayCollect(collector, receipts) ? "ALLOWED" : "BLOCKED"));
                for (String receipt : receipts.split(",")) {
                    var id = Identifier.tryParse(receipt);
                    if (id != null && RecipeStages.blocked(collector, id)) lines.addAll(RecipeStages.explain(collector, id));
                }
            }
        }
        lines.add("Every matching rule must be unlocked or excepted; an exception only cancels its own rule. Item-use settings do not control recipes.");
        lines.add("Permission verdict only: fuel, redstone, output space and other vanilla conditions still apply.");
        if (outputOnly) lines.add("Automation remains permitted; campfire/crafter collection checks use recipe-tagged item drops.");
        return lines;
    }

    public static String stored(BlockEntity entity, boolean placement) {
        String key = placement ? ProcessorRecipes.OWNER : ProcessorRecipes.INSERTER;
        String id = entity.getPersistentData().getStringOr(key, "");
        return (placement ? "Placer" : "Last inserter") + ": " + (id.isEmpty() ? "not recorded" : id)
                + " | fake=" + entity.getPersistentData().getBooleanOr(key + "_fake", false);
    }

    private static RecipeType<?> type(BlockEntity entity) {
        if (entity instanceof CrafterBlockEntity) return RecipeType.CRAFTING;
        if (entity instanceof CampfireBlockEntity) return RecipeType.CAMPFIRE_COOKING;
        if (entity instanceof SmokerBlockEntity) return RecipeType.SMOKING;
        if (entity instanceof BlastFurnaceBlockEntity) return RecipeType.BLASTING;
        return RecipeType.SMELTING;
    }

    private static List<RecipeHolder<?>> candidates(BlockEntity entity) {
        var level = (ServerLevel) entity.getLevel();
        if (entity instanceof CrafterBlockEntity crafter) return matching(level, RecipeType.CRAFTING, crafter.asCraftInput());
        if (entity instanceof CampfireBlockEntity campfire) {
            Map<Identifier, RecipeHolder<?>> recipes = new LinkedHashMap<>();
            for (var stack : campfire.getItems()) if (!stack.isEmpty())
                matching(level, RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack)).forEach(recipe -> recipes.put(recipe.id().identifier(), recipe));
            return List.copyOf(recipes.values());
        }
        var furnace = (AbstractFurnaceBlockEntity) entity;
        if (furnace.getItem(0).isEmpty()) return List.of();
        var input = new SingleRecipeInput(furnace.getItem(0));
        if (entity instanceof SmokerBlockEntity) return matching(level, RecipeType.SMOKING, input);
        if (entity instanceof BlastFurnaceBlockEntity) return matching(level, RecipeType.BLASTING, input);
        return matching(level, RecipeType.SMELTING, input);
    }

    private static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<?>> matching(ServerLevel level, RecipeType<T> type, I input) {
        return level.getServer().getRecipeManager().recipeMap().getRecipesFor(type, input, level).<RecipeHolder<?>>map(recipe -> recipe).toList();
    }
}
