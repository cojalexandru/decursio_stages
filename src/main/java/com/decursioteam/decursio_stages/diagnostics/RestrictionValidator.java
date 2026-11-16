package com.decursioteam.decursio_stages.diagnostics;

import com.decursioteam.decursio_stages.codec.SettingsCodec;
import com.decursioteam.decursio_stages.datagen.*;
import com.decursioteam.decursio_stages.orestaging.OreStages;
import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.decursioteam.decursio_stages.recipes.RecipeRestrictionIndex;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;

import java.util.*;

/** Explicit, potentially expensive checks, run only when an operator requests validation. */
public final class RestrictionValidator {
    private static final List<String> LISTS = List.of("itemList", "tagList", "modList", "fluidList", "exceptionList",
            "structureList", "dimensionList", "mobList", "containerList", "blockReplacementList", "recipeList", "recipeExceptionList");
    private static final Set<String> SETTINGS = Set.of("advancedTooltips", "itemsTitle", "itemsPickupDelay", "hideInJEI_REI",
            "canPickupItems", "dropItemsFromInventory", "dropArmorFromInventory", "canUseItems", "canRightClickBlocks",
            "canBreakBlocks", "containerListWhitelist", "applyToFakePlayers");
    private static final Map<String, Set<String>> OBJECT_FIELDS = Map.ofEntries(
            Map.entry("recipeList", Set.of("recipe", "mod", "tag")),
            Map.entry("recipeExceptionList", Set.of("recipe", "mod", "tag")),
            Map.entry("itemList", Set.of("item", "nbt")),
            Map.entry("fluidList", Set.of("fluid", "tag", "mod")),
            Map.entry("exceptionList", Set.of("item", "tag", "mod", "nbt", "fluid", "fluidTag", "fluidMod")),
            Map.entry("structureList", Set.of("structure", "can_use_block", "can_place_block", "can_break_block", "can_use_block_list", "can_place_block_list", "can_break_block_list")),
            Map.entry("dimensionList", Set.of("dimension", "message")),
            Map.entry("mobList", Set.of("entityID", "entityModID", "entityTag", "mobCategory", "spawnType", "whitelist_blacklist", "minLight", "maxLight", "biomes", "health", "effects", "loadout")),
            Map.entry("effects", Set.of("effect", "amplifier", "duration", "chance")),
            Map.entry("loadout", Set.of("item", "slot", "chance", "nbt")),
            Map.entry("blockReplacementList", Set.of("block", "tag", "replacement")));
    private static final Map<String, List<String>> REQUIRED = Map.of(
            "itemList", List.of("item"), "structureList", List.of("structure"), "dimensionList", List.of("dimension"),
            "mobList", List.of("spawnType", "whitelist_blacklist"), "effects", List.of("effect"),
            "loadout", List.of("item", "slot"), "blockReplacementList", List.of("replacement"));
    private final MinecraftServer server;
    private final List<String> issues = new ArrayList<>();
    private int errors, warnings;
    private String rule;

    private RestrictionValidator(MinecraftServer server) { this.server = server; }

    public static List<String> validate(MinecraftServer server, String only) {
        var validator = new RestrictionValidator(server);
        var registry = RestrictionsData.getRegistry();
        registry.getRawRestrictions().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(entry -> only == null || only.equals(entry.getKey())).forEach(entry -> {
                    validator.rule = entry.getKey();
                    validator.validateRule(entry.getValue());
                });
        validator.conflicts(only);
        List<String> report = new ArrayList<>();
        report.add("Validation: " + (only == null ? registry.getRawRestrictions().size() + " loaded rules" : only)
                + " | " + validator.errors + " errors, " + validator.warnings + " warnings.");
        report.add("Checks use server registries/tags and the current loaded snapshot. Potential conflicts can be intentional; no rules or stages are changed.");
        report.addAll(RestrictionReloadStatus.report());
        report.addAll(validator.issues);
        if (validator.errors + validator.warnings > validator.issues.size()) report.add("Further issues omitted after 1000 entries; filter with /decstages validate rule <name>.");
        if (validator.errors + validator.warnings == 0) report.add("No unknown IDs, empty tags or detected conflicts found.");
        return report;
    }

    private void issue(boolean error, String path, String message) {
        if (error) errors++; else warnings++;
        if (issues.size() < 1000) issues.add((error ? "ERROR " : "WARN ") + rule + " / " + path + ": " + message
                + " [" + RestrictionsData.getRegistry().effectiveSource(rule) + "]");
    }

    private void validateRule(JsonObject root) {
        try { RestrictionSchema.validate(root); }
        catch (RuntimeException error) { issue(true, "schema", error.getMessage()); }
        var sources = RestrictionsData.getRegistry().sources(rule);
        if (sources.size() > 1) issue(false, "source", "Duplicate name; last loaded source wins. Definitions: " + String.join(" -> ", sources));
        if (!root.has("Restriction Data") || !root.get("Restriction Data").isJsonObject()) {
            issue(true, "Restriction Data", "Required object is missing"); return;
        }
        var data = root.getAsJsonObject("Restriction Data");
        if (!data.has("stage") || !data.get("stage").isJsonPrimitive() || !data.get("stage").getAsJsonPrimitive().isString() || data.get("stage").getAsString().isBlank())
            issue(true, "stage", "A non-empty stage is required; a typo creates a different stage");
        else if (!data.get("stage").getAsString().equals(data.get("stage").getAsString().trim().toLowerCase(Locale.ROOT)))
            issue(false, "stage", "Normalize to lowercase without surrounding whitespace; stage APIs normalize input");
        for (String key : data.keySet()) if (!LISTS.contains(key) && !Set.of("stage", "name").contains(key))
            issue(false, key, "Unknown restriction field (ignored); numeric priority is not supported");
        for (String key : LISTS) {
            if (!data.has(key)) continue;
            JsonElement value = data.get(key);
            if (!value.isJsonArray()) { issue(true, key, "Expected a list; a codec fallback may silently ignore this value"); continue; }
            walkList(value.getAsJsonArray(), key, key);
        }
        if (root.has("Settings")) {
            if (!root.get("Settings").isJsonObject()) issue(true, "Settings", "Expected an object");
            else {
                var settings = root.getAsJsonObject("Settings");
                for (var entry : settings.entrySet()) {
                    String key = entry.getKey(); var value = entry.getValue();
                    if (!SETTINGS.contains(key)) issue(false, "Settings." + key, "Unknown setting (ignored)");
                    else if (!value.isJsonPrimitive()) issue(true, "Settings." + key, "Expected a primitive value");
                    else if (!Set.of("advancedTooltips", "itemsTitle", "itemsPickupDelay").contains(key) && !value.getAsJsonPrimitive().isBoolean())
                        issue(true, "Settings." + key, "Expected true or false");
                    else if (key.equals("itemsPickupDelay") && !value.getAsJsonPrimitive().isNumber())
                        issue(true, "Settings." + key, "Expected a number");
                    else if (key.equals("advancedTooltips") && !Set.of("ALWAYS", "ADVANCED", "NONE").contains(value.getAsString()))
                        issue(true, "Settings." + key, "Expected ALWAYS, ADVANCED or NONE");
                    else if (key.equals("itemsTitle") && !value.getAsJsonPrimitive().isString())
                        issue(true, "Settings." + key, "Expected a text string");
                }
            }
        }
        var parsed = RestrictionsData.getRegistry().getRestrictions().get(rule);
        if (parsed != null && !parsed.getData().getRecipeExceptionList().isEmpty()) {
            var includes = parsed.getData().getRecipeList();
            if (includes.isEmpty()) {
                for (var exception : parsed.getData().getRecipeExceptionList())
                    issue(false, "recipeExceptionList", "Exception has no recipe selector in this rule: " + exception.describe());
            } else {
                var displayContext = RecipeRestrictionIndex.displayContext(server);
                var allRecipes = server.getRecipeManager().getRecipes();
                for (var exception : parsed.getData().getRecipeExceptionList()) {
                    boolean overlaps = allRecipes.stream().anyMatch(holder -> {
                        var id = holder.id();
                        return RecipeRestrictionIndex.matchesAny(List.of(exception), id, holder, displayContext)
                                && RecipeRestrictionIndex.matchesAny(includes, id, holder, displayContext);
                    });
                    if (!overlaps) issue(false, "recipeExceptionList", "Exception matches no recipe selected by this rule: " + exception.describe());
                }
            }
        }
        if (parsed != null) for (var replacement : parsed.getData().getBlockReplacementList()) {
            try { OreStages.validate(replacement); }
            catch (RuntimeException ex) { issue(true, "blockReplacementList", ex.getMessage()); }
            replacement.tag().ifPresent(id -> BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).ifPresent(tag -> {
                for (var holder : tag) if (!holder.value().getStateDefinition().getPossibleStates().stream().allMatch(OreStages::supportedSource))
                    issue(true, "blockReplacementList.#" + id, "Unsupported source in tag: " + BuiltInRegistries.BLOCK.getKey(holder.value()));
            }));
        }
    }

    private void walkList(JsonArray array, String path, String context) {
        Set<JsonElement> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            String entryPath = path + "[" + i + "]";
            if (!seen.add(entry)) issue(false, entryPath, "Duplicate entry");
            walk(entry, entryPath, context);
        }
    }

    private void walk(JsonElement value, String path, String context) {
        try {
            if (value.isJsonArray()) {
                issue(true, path, "Unexpected nested list; each entry must be an object or string");
                return;
            } else if (value.isJsonObject()) {
                var object = value.getAsJsonObject();
                if (!OBJECT_FIELDS.containsKey(context)) { issue(true, path, "Expected a string entry, not an object"); return; }
                for (String key : REQUIRED.getOrDefault(context, List.of())) if (!object.has(key)) issue(true, path + "." + key, "Required field is missing");
                for (String key : object.keySet()) if (!OBJECT_FIELDS.get(context).contains(key)) issue(false, path + "." + key, "Unknown field (ignored)");
                if (context.equals("recipeList") || context.equals("recipeExceptionList")) {
                    if (List.of("recipe", "mod", "tag").stream().filter(object::has).count() != 1)
                        issue(true, path, "Choose exactly one recipe ID, mod ID, or output item tag");
                }
                if (context.equals("fluidList")) selectorCount(object, path, List.of("fluid", "tag", "mod"), true);
                if (context.equals("exceptionList")) selectorCount(object, path, List.of("item", "mod", "tag"), false);
                if (context.equals("blockReplacementList") && object.has("block") == object.has("tag"))
                    issue(true, path, "Choose exactly one original block or tag");
                if (context.equals("mobList")) {
                    if (List.of("entityID", "entityModID", "entityTag", "mobCategory").stream().noneMatch(object::has))
                        issue(true, path, "No mob filter; this rule cannot select a mob");
                    if (object.has("whitelist_blacklist") && object.get("whitelist_blacklist").getAsString().equals("BLACKLIST")
                            && List.of("health", "effects", "loadout").stream().anyMatch(object::has))
                        issue(false, path, "Health, effects and equipment apply only to allowed WHITELIST spawns; BLACKLIST uses spawn reason/light/biome conditions");
                }
                for (var entry : object.entrySet()) {
                    String key = entry.getKey();
                    var child = entry.getValue();
                    if (key.equals("nbt")) {
                        if (!child.isJsonObject()) issue(true, path + ".nbt", "Expected a component pattern object");
                        continue; // Component keys are not restriction selectors.
                    }
                    if (Set.of("effects", "loadout", "biomes", "spawnType").contains(key) || key.endsWith("_block_list")) {
                        if (child.isJsonArray()) walkList(child.getAsJsonArray(), path + "." + key, key);
                        else issue(true, path + "." + key, "Expected a list");
                    } else if (child.isJsonPrimitive()) primitive(child, key, path + "." + key, context);
                    else issue(true, path + "." + key, "Unexpected value type");
                }
            } else if (value.isJsonPrimitive()) {
                if (context.equals("recipeList") || context.equals("recipeExceptionList"))
                    primitive(value, "recipe", path, context);
                else if (OBJECT_FIELDS.containsKey(context)) issue(true, path, "Expected an object entry");
                else primitive(value, context, path, context);
            }
            else issue(true, path, "Null entry");
        } catch (RuntimeException ex) { issue(true, path, "Invalid value: " + ex.getMessage()); }
    }

    private void selectorCount(JsonObject object, String path, List<String> keys, boolean required) {
        long count = keys.stream().filter(object::has).count();
        if (count > 1) issue(false, path, "Multiple selectors; precedence is " + String.join(" > ", keys) + ", not AND");
        if (count == 0 && required) issue(true, path, "No selector; rule does nothing");
        if (count == 0 && !required && List.of("fluid", "fluidTag", "fluidMod").stream().noneMatch(object::has))
            issue(false, path, "Exception has no item or fluid selector; does nothing");
    }

    private void primitive(JsonElement value, String key, String path, String context) {
        String text = value.getAsString();
        if (Set.of("can_use_block", "can_place_block", "can_break_block").contains(key)) {
            if (!value.getAsJsonPrimitive().isBoolean()) issue(true, path, "Expected true or false");
            return;
        }
        switch (key) {
            case "recipe", "recipeList", "recipeExceptionList" -> {
                var recipeId = ResourceLocation.tryParse(text);
                var recipe = recipeId == null ? Optional.<RecipeHolder<?>>empty()
                        : server.getRecipeManager().byKey(recipeId);
                if (recipe.isEmpty()) issue(true, path, "Unknown recipe ID: " + text);
                else if (!ProcessorRecipes.supported(recipe.get().value().getType()))
                    issue(false, path, "Recipe type has no execution adapter: " + text);
            }
            case "item" -> id(BuiltInRegistries.ITEM, value, path, false);
            case "fluid" -> id(BuiltInRegistries.FLUID, value, path, false);
            case "effect" -> id(BuiltInRegistries.MOB_EFFECT, value, path, false);
            case "entityID" -> id(BuiltInRegistries.ENTITY_TYPE, value, path, false);
            case "block", "replacement", "can_use_block_list", "can_place_block_list", "can_break_block_list" -> id(BuiltInRegistries.BLOCK, value, path, false);
            case "tagList" -> id(BuiltInRegistries.ITEM, value, path, true);
            case "tag" -> {
                if (context.equals("fluidList")) id(BuiltInRegistries.FLUID, value, path, true);
                else if (context.equals("blockReplacementList")) id(BuiltInRegistries.BLOCK, value, path, true);
                else id(BuiltInRegistries.ITEM, value, path, true);
            }
            case "fluidTag" -> id(BuiltInRegistries.FLUID, value, path, true);
            case "entityTag" -> id(BuiltInRegistries.ENTITY_TYPE, value, path, true);
            case "structure" -> id(server.registryAccess().registryOrThrow(Registries.STRUCTURE), value, path, false);
            case "biomes" -> id(server.registryAccess().registryOrThrow(Registries.BIOME), value, path, false);
            case "dimension" -> {
                ResourceLocation parsed = ResourceLocation.tryParse(text);
                if (parsed == null || server.levelKeys().stream().noneMatch(level -> level.location().equals(parsed)))
                    issue(true, path, "Unknown or unloaded dimension: " + text);
            }
            case "mod", "modList", "fluidMod", "entityModID" -> {
                if ((context.equals("recipeList") || context.equals("recipeExceptionList"))
                        && server.getRecipeManager().getRecipes().stream().noneMatch(r -> r.id().getNamespace().equals(text))) {
                    issue(false, path, (ModList.get().isLoaded(text) ? "No loaded recipes found for mod namespace: " : "Unknown/unloaded mod namespace: ") + text);
                } else if (!ModList.get().isLoaded(text)) {
                    issue(false, path, "Unknown/unloaded mod namespace: " + text);
                }
            }
            case "containerList" -> {
                if (!text.matches("[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)+") && !text.equals("decursio_stages.inventoryMenu.CraftingGrid"))
                    issue(false, path, "Expected a fully qualified Java menu class name (not a registry ID)");
                else if (!text.equals("decursio_stages.inventoryMenu.CraftingGrid")) {
                    try {
                        Class<?> type = Class.forName(text, false, RestrictionValidator.class.getClassLoader());
                        if (!net.minecraft.world.inventory.AbstractContainerMenu.class.isAssignableFrom(type))
                            issue(false, path, "Class is not a container menu: " + text);
                    } catch (ClassNotFoundException | LinkageError ex) { issue(false, path, "Unknown/unavailable menu class: " + text); }
                }
            }
            default -> { }
        }
    }

    private <T> void id(Registry<T> registry, JsonElement value, String path, boolean tag) {
        if (!value.getAsJsonPrimitive().isString()) { issue(true, path, "Expected a resource ID string"); return; }
        ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
        if (id == null) { issue(true, path, "Invalid resource ID: " + value); return; }
        if (tag) {
            if (registry.getTag(TagKey.create(registry.key(), id)).map(set -> set.size() == 0).orElse(true))
                issue(false, path, "Empty or unknown tag: #" + id + " (matches nothing)");
        } else if (!registry.containsKey(id)) issue(true, path, "Unknown ID in " + registry.key().location() + ": " + id);
    }

    private void conflicts(String only) {
        var registry = RestrictionsData.getRegistry();
        List<String> names = registry.getRestrictions().keySet().stream().sorted().toList();
        Set<List<String>> reported = new HashSet<>();
        Map<String, JsonElement> effectiveSettings = new HashMap<>();
        names.forEach(name -> effectiveSettings.put(name, settings(registry.getRestrictions().get(name))));
        // Probe default stacks: proves broad item/tag/mod overlap without guessing component patterns.
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;
            List<String> matching = names.stream().filter(name -> registry.compiled(name).matches(stack, ResourceUtil.CHECK_TYPES.ALL)).toList();
            for (int i = 0; i < matching.size(); i++) for (int j = i + 1; j < matching.size(); j++) {
                String a = matching.get(i), b = matching.get(j);
                if (only != null && !a.equals(only) && !b.equals(only)) continue;
                if (!reported.add(List.of(a, b))) continue;
                var first = registry.getRestrictions().get(a);
                var second = registry.getRestrictions().get(b);
                if (first.getData().getStage().equals(second.getData().getStage()) && effectiveSettings.get(a).equals(effectiveSettings.get(b))) continue;
                rule = only == null ? a : only;
                issue(false, "overlap", a + " overlaps " + b + " on " + BuiltInRegistries.ITEM.getKey(item)
                        + "; stages=" + first.getData().getStage() + "/" + second.getData().getStage()
                        + ". Denials accumulate; an allow/exception in one rule cannot override the other. Other source: " + registry.effectiveSource(only != null && only.equals(b) ? a : b));
            }
        }
        // Identical selectors prove overlaps even when default-stack probes cannot match component patterns.
        for (String list : List.of("itemList", "dimensionList", "structureList", "mobList")) {
            Map<JsonElement, List<String>> selectors = new HashMap<>();
            for (String name : names) {
                JsonObject raw = registry.getRawRestrictionData(name);
                if (raw == null || !raw.has("Restriction Data")) continue;
                JsonObject data = raw.getAsJsonObject("Restriction Data");
                if (!data.has(list) || !data.get(list).isJsonArray()) continue;
                for (var entry : data.getAsJsonArray(list)) {
                    if (!entry.isJsonObject()) continue;
                    JsonObject object = entry.getAsJsonObject(), selector = object.deepCopy();
                    if (list.equals("dimensionList")) selector.remove("message");
                    if (list.equals("structureList")) selector.keySet().removeIf(key -> !key.equals("structure"));
                    if (list.equals("mobList")) selector.keySet().removeIf(key -> !Set.of("entityID", "entityModID", "entityTag", "mobCategory").contains(key));
                    var previous = selectors.computeIfAbsent(selector, key -> new ArrayList<>());
                    for (String other : previous) {
                        if (other.equals(name) || only != null && !other.equals(only) && !name.equals(only)) continue;
                        if (list.equals("itemList") && reported.contains(List.of(other, name))) continue;
                        rule = only == null ? name : only;
                        issue(false, list, "Potential overlap with " + other + ": identical selector " + selector
                                + ". Rules can be active together; compare stages and action settings with /decstages rule. Other source: " + registry.effectiveSource(other));
                    }
                    if (!previous.contains(name)) previous.add(name);
                }
            }
        }
        Set<List<String>> fluidPairs = new HashSet<>();
        for (var fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY) continue;
            List<String> matching = names.stream().filter(name -> ResourceUtil.isFluidRestricted(name, fluid)).toList();
            for (int i = 0; i < matching.size(); i++) for (int j = i + 1; j < matching.size(); j++) {
                String a = matching.get(i), b = matching.get(j);
                if (only != null && !a.equals(only) && !b.equals(only) || !fluidPairs.add(List.of(a, b))) continue;
                var first = registry.getRestrictions().get(a); var second = registry.getRestrictions().get(b);
                if (first.getData().getStage().equals(second.getData().getStage()) && first.getSettingsCodec().getHideInJEI() == second.getSettingsCodec().getHideInJEI()) continue;
                rule = only == null ? a : only;
                issue(false, "fluid overlap", a + " and " + b + " both match " + BuiltInRegistries.FLUID.getKey(fluid)
                        + "; stages=" + first.getData().getStage() + "/" + second.getData().getStage() + ". Viewer hiding accumulates; exceptions are local to each rule.");
            }
        }
        record Ore(String name, int index, ResourceLocation target) {}
        Map<ResourceLocation, Ore> ores = new HashMap<>();
        for (String name : names) {
            var replacements = registry.getRestrictions().get(name).getData().getBlockReplacementList();
            for (int index = 0; index < replacements.size(); index++) {
                var replacement = replacements.get(index);
                try { OreStages.validate(replacement); }
                catch (RuntimeException ignored) { continue; } // Enforcement skips invalid replacement rules.
                Set<ResourceLocation> sources = new TreeSet<>();
                replacement.block().ifPresent(sources::add);
                replacement.tag().ifPresent(id -> BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).ifPresent(tag ->
                        tag.forEach(holder -> sources.add(BuiltInRegistries.BLOCK.getKey(holder.value())))));
                for (var source : sources) {
                    if (!BuiltInRegistries.BLOCK.containsKey(source) || !BuiltInRegistries.BLOCK.get(source).getStateDefinition()
                            .getPossibleStates().stream().allMatch(OreStages::supportedSource)) continue;
                    var previous = ores.putIfAbsent(source, new Ore(name, index, replacement.replacement()));
                    if (previous != null && !previous.target().equals(replacement.replacement()) && (only == null || name.equals(only) || previous.name().equals(only))) {
                        rule = name;
                        issue(false, "blockReplacementList[" + index + "]", "Conflicting replacement for " + source + ": " + previous.name() + "[" + previous.index()
                                + "] -> " + previous.target() + " wins over " + name + " -> " + replacement.replacement() + " when both rules are active. Ore order: alphabetical name, then list order.");
                    }
                }
            }
        }
    }

    private static JsonElement settings(Restrictions restriction) {
        return SettingsCodec.CODEC.encodeStart(JsonOps.INSTANCE, restriction.getSettingsCodec()).getOrThrow();
    }
}
