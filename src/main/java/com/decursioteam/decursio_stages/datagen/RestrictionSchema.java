package com.decursioteam.decursio_stages.datagen;

import com.google.gson.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import java.util.*;

/** Reject explicitly malformed fields before legacy codecs can silently substitute defaults. */
public final class RestrictionSchema {
    private RestrictionSchema() {}
    private static final Map<String, String> DATA = Map.ofEntries(
            Map.entry("recipeList", "recipeSelector[]"), Map.entry("recipeExceptionList", "recipeSelector[]"), Map.entry("stage", "text!"), Map.entry("name", "text"), Map.entry("itemList", "item[]"),
            Map.entry("tagList", "id[]"), Map.entry("modList", "text[]"), Map.entry("fluidList", "fluid[]"),
            Map.entry("exceptionList", "exception[]"), Map.entry("dimensionList", "dimension[]"),
            Map.entry("structureList", "structure[]"), Map.entry("mobList", "mob[]"),
            Map.entry("containerList", "text[]"), Map.entry("blockReplacementList", "ore[]"));
    private static final Map<String, String> SETTINGS = new LinkedHashMap<>();
    static {
        for (String key : List.of("hideInJEI_REI", "canPickupItems", "dropItemsFromInventory", "dropArmorFromInventory",
                "canUseItems", "canRightClickBlocks", "canBreakBlocks", "containerListWhitelist", "applyToFakePlayers")) SETTINGS.put(key, "bool");
        SETTINGS.put("advancedTooltips", "text"); SETTINGS.put("itemsTitle", "text"); SETTINGS.put("itemsPickupDelay", "int");
    }
    private static final Map<String, Map<String, String>> OBJECTS = Map.ofEntries(
            Map.entry("recipeSelector", Map.of("recipe", "id", "mod", "text", "tag", "id")),
            Map.entry("item", Map.of("item", "id!", "nbt", "object")),
            Map.entry("fluid", Map.of("fluid", "id", "tag", "id", "mod", "text")),
            Map.entry("exception", Map.of("item", "id", "tag", "id", "mod", "text", "fluid", "id", "fluidTag", "id", "fluidMod", "text", "nbt", "object")),
            Map.entry("dimension", Map.of("dimension", "id!", "message", "text")),
            Map.entry("structure", Map.of("structure", "id!", "can_use_block", "bool", "can_place_block", "bool", "can_break_block", "bool",
                    "can_use_block_list", "id[]", "can_place_block_list", "id[]", "can_break_block_list", "id[]")),
            Map.entry("mob", Map.ofEntries(Map.entry("entityID", "id"), Map.entry("entityModID", "text"), Map.entry("entityTag", "id"),
                    Map.entry("mobCategory", "text"), Map.entry("spawnType", "text[]!"), Map.entry("whitelist_blacklist", "text!"),
                    Map.entry("minLight", "int"), Map.entry("maxLight", "int"), Map.entry("biomes", "id[]"), Map.entry("health", "int"),
                    Map.entry("effects", "effect[]"), Map.entry("loadout", "equipment[]"))),
            Map.entry("effect", Map.of("effect", "id!", "amplifier", "int", "duration", "int", "chance", "int")),
            Map.entry("equipment", Map.of("item", "id!", "slot", "text!", "chance", "int", "nbt", "object")),
            Map.entry("ore", Map.of("block", "id", "tag", "id", "replacement", "id!")));

    public static void validate(JsonObject json) {
        object(json.get("Restriction Data"), DATA, "Restriction Data");
        if (json.getAsJsonObject("Restriction Data").get("stage").getAsString().isBlank()) fail("Restriction Data.stage", "must not be blank");
        if (json.has("Settings")) object(json.get("Settings"), SETTINGS, "Settings");
    }
    private static void object(JsonElement value, Map<String, String> fields, String path) {
        if (value == null || !value.isJsonObject()) fail(path, "expected an object");
        JsonObject object = value.getAsJsonObject();
        fields.forEach((key, type) -> {
            boolean required = type.endsWith("!");
            if (!object.has(key)) { if (required) fail(path + "." + key, "required field is missing"); return; }
            check(object.get(key), required ? type.substring(0, type.length() - 1) : type, path + "." + key);
            semanticValue(key, object.get(key), path + "." + key);
        });
    }
    private static void check(JsonElement value, String type, String path) {
        if (type.endsWith("[]")) {
            if (!value.isJsonArray()) fail(path, "expected a list");
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) check(array.get(i), type.substring(0, type.length() - 2), path + "[" + i + "]");
        } else if (type.equals("recipeSelector")) {
            if (value != null && value.isJsonPrimitive()) check(value, "id", path);
            else {
                object(value, OBJECTS.get(type), path);
                semanticObject(type, value.getAsJsonObject(), path);
            }
        } else if (OBJECTS.containsKey(type)) {
            object(value, OBJECTS.get(type), path);
            semanticObject(type, value.getAsJsonObject(), path);
        }
        else if (type.equals("object")) { if (!value.isJsonObject()) fail(path, "expected an object"); }
        else {
            if (!value.isJsonPrimitive()) fail(path, "expected " + type);
            var primitive = value.getAsJsonPrimitive();
            switch (type) {
                case "text", "id" -> {
                    if (!primitive.isString()) fail(path, "expected a string");
                    if (type.equals("id") && (primitive.getAsString().isBlank() || Identifier.tryParse(primitive.getAsString()) == null)) fail(path, "invalid resource ID");
                }
                case "bool" -> { if (!primitive.isBoolean()) fail(path, "expected true or false"); }
                case "int" -> {
                    if (!primitive.isNumber()) fail(path, "expected an integer");
                    try { primitive.getAsBigDecimal().intValueExact(); }
                    catch (ArithmeticException | NumberFormatException ex) { fail(path, "expected a 32-bit whole number"); }
                }
                default -> throw new IllegalStateException(type);
            }
        }
    }
    /** Shared entry point for individual GUI fields and lists, using the same server schema. */
    public static void validateField(String key, JsonElement value, boolean required, String path) {
        if (value == null) { if (required) fail(path, "required field is missing"); return; }
        String type = DATA.get(key);
        if (type == null) type = SETTINGS.get(key);
        if (type == null) for (var fields : OBJECTS.values()) if (fields.containsKey(key)) { type = fields.get(key); break; }
        if (type == null) throw new IllegalArgumentException("Unknown field: " + key);
        if (type.endsWith("!")) type = type.substring(0, type.length() - 1);
        check(value, type, path);
        semanticValue(key, value, path);
    }

    private static void semanticValue(String key, JsonElement value, String path) {
        switch (key) {
            case "stage", "name", "mod", "fluidMod", "entityModID", "modList", "containerList" -> {
                if (value.isJsonArray()) {
                    for (int i = 0; i < value.getAsJsonArray().size(); i++) semanticValue(key.equals("modList") ? "mod" : "menuClass", value.getAsJsonArray().get(i), path + "[" + i + "]");
                } else {
                    if (value.getAsString().isBlank()) fail(path, "must not be blank");
                    if (Set.of("mod", "fluidMod", "entityModID").contains(key) && !value.getAsString().matches("[a-z0-9_.-]+")) fail(path, "invalid mod namespace");
                }
            }
            case "menuClass" -> { if (!value.getAsString().matches("[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+")) fail(path, "expected a fully qualified menu class name"); }
            case "advancedTooltips" -> choice(value, path, Set.of("ALWAYS", "ADVANCED", "NONE"), false);
            case "whitelist_blacklist" -> choice(value, path, Set.of("BLACKLIST", "WHITELIST"), false);
            case "slot" -> choice(value, path, Set.of("MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET"), true);
            case "mobCategory" -> choice(value, path, Arrays.stream(MobCategory.values()).map(MobCategory::getName).collect(java.util.stream.Collectors.toSet()), false);
            case "spawnType" -> {
                if (value.getAsJsonArray().isEmpty()) fail(path, "select at least one spawn reason");
                for (var reason : value.getAsJsonArray()) {
                    try { EntitySpawnReason.valueOf(reason.getAsString()); }
                    catch (IllegalArgumentException error) { fail(path, "unknown spawn reason: " + reason.getAsString()); }
                }
            }
            case "chance" -> range(value, path, 0, 100);
            case "amplifier" -> range(value, path, 0, 255);
            case "duration" -> range(value, path, -1, Integer.MAX_VALUE);
            case "health" -> range(value, path, 1, Integer.MAX_VALUE);
            case "minLight", "maxLight" -> range(value, path, 0, 15);
            case "itemsPickupDelay" -> range(value, path, 0, Integer.MAX_VALUE);
            default -> { }
        }
    }

    private static void semanticObject(String type, JsonObject value, String path) {
        if (type.equals("recipeSelector") && List.of("recipe", "mod", "tag").stream().filter(value::has).count() != 1)
            fail(path, "choose exactly one recipe ID, mod ID, or output item tag");
        if (type.equals("mob")) {
            if (List.of("entityID", "entityModID", "entityTag", "mobCategory").stream().noneMatch(value::has)) fail(path, "choose at least one mob filter");
            if (value.has("minLight") && value.has("maxLight") && value.get("minLight").getAsInt() > value.get("maxLight").getAsInt()) fail(path, "minimum light exceeds maximum light");
        }
        if (type.equals("ore") && value.has("block") == value.has("tag")) fail(path, "choose exactly one original block or tag");
        if (type.equals("fluid") && List.of("fluid", "tag", "mod").stream().noneMatch(value::has)) fail(path, "choose a fluid, tag or mod selector");
        if (type.equals("exception") && List.of("item", "tag", "mod", "fluid", "fluidTag", "fluidMod").stream().noneMatch(value::has)) fail(path, "exception has no selector");
    }

    private static void choice(JsonElement value, String path, Set<String> choices, boolean uppercase) {
        String text = value.getAsString();
        if (!choices.contains(uppercase ? text.toUpperCase(Locale.ROOT) : text)) fail(path, "unsupported value: " + text);
    }
    private static void range(JsonElement value, String path, int min, int max) {
        int number = value.getAsInt();
        if (number < min || number > max) fail(path, "expected a whole number from " + min + " to " + max);
    }
    private static void fail(String path, String message) { throw new IllegalArgumentException(path + ": " + message); }
}
