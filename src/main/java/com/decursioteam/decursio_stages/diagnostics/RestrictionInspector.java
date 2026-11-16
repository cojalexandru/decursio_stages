package com.decursioteam.decursio_stages.diagnostics;

import com.decursioteam.decursio_stages.codec.SettingsCodec;
import com.decursioteam.decursio_stages.datagen.*;
import com.decursioteam.decursio_stages.utils.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

/** On-demand server diagnostics. Outcomes use the same compiled matcher as enforcement. */
public final class RestrictionInspector {
    private RestrictionInspector() {}

    public record Match(String rule, String stage, boolean stagePresent, boolean storedStage,
                        boolean matches, List<String> conditions, List<String> exceptions, List<String> rejectedExceptions) {}

    public static List<Match> matches(ItemStack stack, Predicate<String> hasStage, Predicate<String> stored) {
        List<Match> result = new ArrayList<>();
        var registry = RestrictionsData.getRegistry();
        Identifier id = ResourceUtil.getRegistryName(stack.getItem());
        registry.getRestrictions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var data = entry.getValue().getData();
            List<String> conditions = new ArrayList<>(), exceptions = new ArrayList<>(), rejected = new ArrayList<>();
            for (int i = 0; i < data.getItemList().size(); i++) {
                var item = data.getItemList().get(i);
                if (id.equals(item.getResourceLocation())) {
                    boolean nbt = NBTComparisonUtil.doesItemMatchRestriction(item, stack);
                    conditions.add("itemList[" + i + "]: " + id + (item.getCompoundNBT() == null ? " (any components)"
                            : " (component pattern " + (nbt ? "MATCH" : "NO MATCH") + ")"));
                }
            }
            for (int i = 0; i < data.getTagList().size(); i++) {
                var tag = data.getTagList().get(i);
                if (stack.is(TagKey.create(Registries.ITEM, tag))) conditions.add("tagList[" + i + "]: #" + tag);
            }
            for (int i = 0; i < data.getModList().size(); i++)
                if (id.getNamespace().equals(data.getModList().get(i))) conditions.add("modList[" + i + "]: @" + id.getNamespace());
            for (int i = 0; i < data.getExceptionList().size(); i++) {
                var exception = data.getExceptionList().get(i);
                // Exactly the selector precedence used by CompiledRestriction.
                boolean applies = exception.getResourceLocation() != null
                        ? id.equals(exception.getResourceLocation()) && (exception.getCompoundNBT() == null
                        || NBTComparisonUtil.areNBTCompoundsEqual(exception.getCompoundNBT(), ItemDataUtil.get(stack)))
                        : exception.getMod() != null ? id.getNamespace().equals(exception.getMod())
                        : exception.getTag() != null && stack.is(TagKey.create(Registries.ITEM, exception.getTag()));
                if (!applies) {
                    boolean modMatch = id.getNamespace().equals(exception.getMod());
                    boolean tagMatch = exception.getTag() != null && stack.is(TagKey.create(Registries.ITEM, exception.getTag()));
                    if (id.equals(exception.getResourceLocation())) rejected.add("exceptionList[" + i + "]: item ID matches but component pattern NO MATCH");
                    else if (exception.getResourceLocation() != null && (modMatch || tagMatch))
                        rejected.add("exceptionList[" + i + "]: matching mod/tag selector is ignored; item '" + exception.getResourceLocation() + "' takes precedence");
                    else if (exception.getMod() != null && tagMatch)
                        rejected.add("exceptionList[" + i + "]: matching tag selector is ignored; mod '" + exception.getMod() + "' takes precedence");
                }
                if (applies) exceptions.add("exceptionList[" + i + "]: " + (exception.getResourceLocation() != null
                        ? exception.getResourceLocation() + (exception.getCompoundNBT() == null ? " (any components)" : " (component pattern MATCH)")
                        : exception.getMod() != null ? "@" + exception.getMod() : "#" + exception.getTag()));
            }
            if (!conditions.isEmpty() || !exceptions.isEmpty() || !rejected.isEmpty()) {
                String stage = data.getStage().toLowerCase(Locale.ROOT);
                result.add(new Match(entry.getKey(), stage, hasStage.test(stage), stored.test(data.getStage()),
                        registry.compiled(entry.getKey()).matches(stack, ResourceUtil.CHECK_TYPES.ALL), List.copyOf(conditions), List.copyOf(exceptions), List.copyOf(rejected)));
            }
        });
        return result;
    }

    public static List<String> explain(ServerPlayer player, ItemStack stack) {
        var stored = StageUtil.getPlayerData(player);
        List<String> lines = new ArrayList<>();
        lines.add("Player: " + player.getName().getString() + " | item: " + ResourceUtil.getRegistryName(stack.getItem())
                + " | components: " + (ItemDataUtil.has(stack) ? "present (pattern matching enabled)" : "default"));
        lines.add("Priority: cumulative; ANY active matching rule can deny. Exceptions bypass only their own rule.");
        var matches = matches(stack, stage -> StageUtil.hasStage(player, stage), stage -> stored != null && stored.hasStage(stage));
        Map<String, List<String>> actions = new LinkedHashMap<>();
        for (String action : List.of("Use/attack", "Pickup", "Inventory removal", "Armor removal", "Recipe viewer hiding", "Current menu removal", "Crafting grid removal"))
            actions.put(action, new ArrayList<>());
        Set<String> stages = new TreeSet<>(), viewerStages = new TreeSet<>();
        String menu = player.containerMenu.getClass().getName();
        boolean fake = player instanceof net.neoforged.neoforge.common.util.FakePlayer;
        for (var match : matches) {
            var settings = RestrictionsData.getRestrictionData(match.rule()).getSettingsCodec();
            if (match.matches() && !match.stagePresent()) {
                stages.add(match.stage());
                if (!settings.getUsableItems()) actions.get("Use/attack").add(match.rule());
                if (!settings.getCanPickup()) actions.get("Pickup").add(match.rule());
                if (!fake || settings.getApplyToFakePlayers()) {
                    if (settings.getCheckPlayerInventory()) actions.get("Inventory removal").add(match.rule());
                    if (settings.getCheckPlayerEquipment()) actions.get("Armor removal").add(match.rule());
                    if (settings.getContainerListWhitelist() != ResourceUtil.getContainers(match.rule()).contains(menu)
                            && player.containerMenu != player.inventoryMenu) actions.get("Current menu removal").add(match.rule());
                    if (settings.getContainerListWhitelist() != ResourceUtil.getContainers(match.rule()).contains("decursio_stages.inventoryMenu.CraftingGrid"))
                        actions.get("Crafting grid removal").add(match.rule());
                }
            }
            if (match.matches() && !match.storedStage() && settings.getHideInJEI()) {
                actions.get("Recipe viewer hiding").add(match.rule()); viewerStages.add(match.stage());
            }
        }
        actions.forEach((action, rules) -> lines.add(action + ": " + (rules.isEmpty() ? "allowed by item rules" : "BLOCKED by " + String.join(", ", rules))));
        lines.add("Menu context: " + menu + "; armor removal is checked when armor changes. Block interactions and structure rules are separate contexts.");
        lines.add(stages.isEmpty() ? "No missing-stage item rule currently applies."
                : "To bypass all active item rules, grant: " + String.join(", ", stages) + ". Stage Check scripts may override stored stages; inspect the player to verify.");
        if (!viewerStages.isEmpty()) lines.add("Viewer visibility also requires stored stages: " + String.join(", ", viewerStages) + ". Granting only an effective Check override does not sync a stored stage.");
        lines.add("Alternative: add an item/tag/mod exception to EACH blocking rule. An exception in one rule cannot override another.");
        if (matches.isEmpty()) lines.add("No item ID, item tag, mod selector or item exception references this stack.");
        for (var match : matches) {
            lines.add("Rule " + match.rule() + ": " + (!match.matches() ? !match.exceptions().isEmpty() ? "EXCEPTED" : "CONDITIONS NOT MET"
                    : match.stagePresent() ? "UNLOCKED" : "ACTIVE (stage missing)") + " | stage=" + match.stage()
                    + " (effective=" + match.stagePresent() + ", stored=" + match.storedStage() + ")");
            lines.add("Source: " + RestrictionsData.getRegistry().effectiveSource(match.rule()));
            match.conditions().forEach(reason -> lines.add("  Condition: " + reason));
            match.exceptions().forEach(reason -> lines.add("  Bypass: " + reason));
            match.rejectedExceptions().forEach(reason -> lines.add("  Exception did not bypass: " + reason));
            lines.add("  Change result: " + (!match.exceptions().isEmpty() ? "remove matching exceptions to make this rule eligible"
                    : match.matches() ? match.stagePresent() ? "remove stage '" + match.stage() + "' to activate this rule"
                    : "grant stage '" + match.stage() + "' or add a matching exception in this rule (GUI: /decstages gui " + match.rule() + ")"
                    : "supply components matching the itemList pattern; matching tag/mod selectors can also apply"));
            lines.addAll(settings(RestrictionsData.getRestrictionData(match.rule()).getSettingsCodec()));
            lines.add("  Inspect all conditions: /decstages rule " + match.rule());
        }
        lines.add("Scope: server item rules now; other mods, world protection and client-only stage checks can change observed behavior. Viewer hiding uses synced stored stages.");
        return lines;
    }

    public static List<String> settings(SettingsCodec settings) {
        JsonObject json = SettingsCodec.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow().getAsJsonObject();
        List<String> lines = new ArrayList<>();
        List<String> group = new ArrayList<>();
        json.entrySet().forEach(entry -> {
            group.add(entry.getKey() + "=" + entry.getValue());
            if (group.size() == 3) { lines.add("  Effective settings: " + String.join(", ", group)); group.clear(); }
        });
        if (!group.isEmpty()) lines.add("  Effective settings: " + String.join(", ", group));
        return lines;
    }

    public static List<String> rule(String name) {
        List<String> lines = new ArrayList<>();
        var registry = RestrictionsData.getRegistry();
        JsonObject raw = registry.getRawRestrictionData(name);
        if (raw == null) return List.of("Unknown restriction: " + name);
        lines.add("Rule: " + name + " | source: " + registry.effectiveSource(name));
        List<String> sources = registry.sources(name);
        for (int i = 0; i + 1 < sources.size(); i++) lines.add("Shadowed source: " + sources.get(i));
        lines.add("Priority: item rules accumulate (deny wins); exceptions apply only inside this rule. No numeric priority exists.");
        lines.add("Ore priority: first valid replacement per source block; across active rules, alphabetical rule name wins.");
        lines.add("Mob denials accumulate. Allowed whitelist modifiers run in alphabetical rule order, then list order; later health/equipment values win.");
        var rule = registry.getRestrictions().get(name);
        if (rule != null) {
            lines.add("Stage: " + rule.getData().getStage() + ". Missing stage activates item/fluid/dimension/ore rules; HAS stage activates structure/mob rules.");
            lines.addAll(settings(rule.getSettingsCodec()));
        }
        if (raw.has("Restriction Data") && raw.get("Restriction Data").isJsonObject()) {
            raw.getAsJsonObject("Restriction Data").entrySet().forEach(entry -> {
                if (entry.getValue().isJsonArray()) {
                    var array = entry.getValue().getAsJsonArray();
                    if (array.isEmpty()) lines.add(entry.getKey() + ": empty");
                    for (int i = 0; i < array.size(); i++) lines.add(entry.getKey() + "[" + i + "] = " + compact(array.get(i)));
                } else lines.add(entry.getKey() + " = " + compact(entry.getValue()));
            });
        }
        lines.add("Item exception selector precedence: item > mod > tag. Fluid exceptions are evaluated separately: fluid, fluidTag and fluidMod are alternatives (OR).");
        return lines;
    }

    private static String compact(JsonElement value) {
        String text = value.toString();
        return text.length() > 700 ? text.substring(0, 697) + "... (long component data omitted)" : text;
    }

    public static List<String> stages(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        var data = StageUtil.getPlayerData(player);
        Set<String> stored = data == null ? Set.of() : new TreeSet<>(data.getStages());
        lines.add("Player: " + player.getName().getString() + " | UUID: " + player.getUUID() + " | dimension: " + player.level().dimension().identifier());
        lines.add("Stored stages (" + stored.size() + "): " + (stored.isEmpty() ? "none" : String.join(", ", stored)));
        if (data == null) lines.add("WARNING: no server stage data exists for this player; stage checks return false.");
        Set<String> known = new TreeSet<>(stored);
        var registry = RestrictionsData.getRegistry();
        registry.getRestrictions().values().forEach(rule -> known.add(rule.getData().getStage().toLowerCase(Locale.ROOT)));
        for (String stage : known) {
            boolean effective = StageUtil.hasStage(player, stage);
            List<String> linked = new ArrayList<>();
            registry.getRestrictions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (entry.getValue().getData().getStage().equalsIgnoreCase(stage)) linked.add(entry.getKey());
            });
            lines.add(stage + ": stored=" + stored.contains(stage) + ", effective=" + effective
                    + (effective != stored.contains(stage) ? " [OVERRIDDEN by a stage Check handler]" : "")
                    + " | rules: " + (linked.isEmpty() ? "none (unreferenced stored stage)" : String.join(", ", linked)));
        }
        lines.addAll(com.decursioteam.decursio_stages.persistence.StageSaveStatus.report(player.getUUID()));
        lines.add("Effective checks include stage Check events/KubeJS on this server. Client recipe viewers use synchronized stored stages.");
        lines.add("Use /decstages explain " + player.getName().getString() + " to diagnose the item in this player's main hand.");
        return lines;
    }
}
