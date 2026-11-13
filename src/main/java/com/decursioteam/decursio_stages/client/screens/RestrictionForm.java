package com.decursioteam.decursio_stages.client.screens;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Field definitions shared by the form widgets and their validation. */
final class RestrictionForm {
    enum Kind { TEXT, ID, NUMBER, BOOLEAN, CHOICE, IDS, STRINGS, CHOICES, OBJECTS, COMPONENTS }

    record Field(String key, String label, Kind kind, boolean required, int min, int max,
                 List<String> choices, List<Field> children, String hint) {
        boolean list() {
            return kind == Kind.IDS || kind == Kind.STRINGS || kind == Kind.CHOICES || kind == Kind.OBJECTS;
        }
    }

    private static Field field(String key, String label, Kind kind, boolean required, String hint) {
        return new Field(key, label, kind, required, 0, Integer.MAX_VALUE, List.of(), List.of(), hint);
    }

    private static Field number(String key, String label, boolean required, int min, int max) {
        return new Field(key, label, Kind.NUMBER, required, min, max, List.of(), List.of(),
                "Whole number, " + min + " to " + max + (required ? "." : ". Leave blank to use the default."));
    }

    private static Field choice(String key, String label, boolean required, List<String> choices) {
        return new Field(key, label, Kind.CHOICE, required, 0, 0, choices, List.of(), "Click to cycle through choices.");
    }

    private static Field objects(String key, String label, List<Field> children) {
        return new Field(key, label, Kind.OBJECTS, false, 0, 0, List.of(), children, "Add, edit or remove entries.");
    }

    private static Field id(String key, String label, boolean required) {
        return field(key, label, Kind.ID, required, "Resource ID, e.g. minecraft:zombie."
                + (required ? " Required." : " Leave blank to disable this filter."));
    }

    private static final List<Field> EFFECT = List.of(
            id("effect", "Effect ID", true), number("amplifier", "Amplifier (0 = level I)", false, 0, 255),
            number("duration", "Duration (ticks; 0 = infinite)", false, -1, Integer.MAX_VALUE),
            number("chance", "Chance (%)", false, 0, 100));
    private static final List<Field> LOADOUT = List.of(
            id("item", "Item ID", true),
            choice("slot", "Equipment slot", true, List.of("MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET")),
            number("chance", "Chance (%)", false, 0, 100),
            field("nbt", "Item components", Kind.COMPONENTS, false,
                    "Copy the held item and its components, or clear the components. Existing custom data is preserved."));
    private static final List<Field> STRUCTURE = List.of(
            id("structure", "Structure ID", true),
            field("can_use_block", "Allow using blocks", Kind.BOOLEAN, false, "Allow all block use inside this structure."),
            field("can_use_block_list", "Use exceptions", Kind.IDS, false, "Block IDs usable even when block use is disabled."),
            field("can_place_block", "Allow placing blocks", Kind.BOOLEAN, false, "Allow all block placement inside this structure."),
            field("can_place_block_list", "Place exceptions", Kind.IDS, false, "Block IDs placeable even when placement is disabled."),
            field("can_break_block", "Allow breaking blocks", Kind.BOOLEAN, false, "Allow all block breaking inside this structure."),
            field("can_break_block_list", "Break exceptions", Kind.IDS, false, "Block IDs breakable even when breaking is disabled."));
    private static final List<Field> DIMENSION = List.of(
            id("dimension", "Dimension ID", true),
            field("message", "Denied-entry message", Kind.TEXT, false, "Message shown when access is denied."));
    private static final List<Field> MOB = List.of(
            id("entityID", "Entity ID", false),
            field("entityModID", "Entity mod ID", Kind.TEXT, false, "Mod namespace, e.g. minecraft. Any matching filter selects the mob."),
            id("entityTag", "Entity tag ID", false),
            choice("mobCategory", "Mob category", false, Arrays.stream(MobCategory.values()).map(MobCategory::getName).toList()),
            choice("whitelist_blacklist", "Spawn rule", true, List.of("BLACKLIST", "WHITELIST")),
            new Field("spawnType", "Spawn reasons", Kind.CHOICES, true, 0, 0,
                    Arrays.stream(EntitySpawnReason.values()).map(Enum::name).toList(), List.of(), "Select one or more spawn reasons."),
            number("minLight", "Minimum light", false, 0, 15),
            number("maxLight", "Maximum light", false, 0, 15),
            field("biomes", "Biomes", Kind.IDS, false, "Optional biome IDs for this rule."),
            number("health", "Health (half-hearts)", false, 1, Integer.MAX_VALUE),
            objects("effects", "Effects", EFFECT), objects("loadout", "Equipment", LOADOUT));

    static final List<Field> SECTIONS = List.of(
            objects("structureList", "Structures", STRUCTURE), objects("dimensionList", "Dimensions", DIMENSION),
            objects("mobList", "Mobs", MOB),
            field("containerList", "Containers", Kind.STRINGS, false, "Java menu class names, e.g. net.minecraft.world.inventory.ChestMenu."),
            objects("blockReplacementList", "Ores", List.of(id("block", "Original block", false),
                    id("tag", "Original block tag", false), id("replacement", "Replacement block", true))));

    static JsonObject newEntry(Field list) {
        JsonObject entry = new JsonObject();
        for (Field field : list.children()) {
            if (field.kind() == Kind.BOOLEAN) entry.addProperty(field.key(), false);
            else if (field.kind() == Kind.IDS && list.key().equals("structureList")) entry.add(field.key(), new JsonArray());
            else if (field.required()) {
                switch (field.kind()) {
                    case CHOICE -> entry.addProperty(field.key(), field.choices().getFirst());
                    case NUMBER -> entry.addProperty(field.key(), switch (field.key()) {
                        case "chance", "duration" -> 100;
                        default -> Math.max(0, field.min());
                    });
                    case CHOICES -> {
                        JsonArray reasons = new JsonArray();
                        reasons.add("NATURAL");
                        entry.add(field.key(), reasons);
                    }
                    default -> entry.addProperty(field.key(), "");
                }
            }
        }
        if (list.key().equals("dimensionList")) entry.addProperty("message", "You don't have access to this dimension");
        return entry;
    }

    static String defaultText(Field field) {
        return switch (field.key()) {
            case "chance", "duration" -> "100";
            case "amplifier" -> "1";
            case "message" -> "You don't have access to this dimension";
            default -> "";
        };
    }

    static void validate(Field field, JsonElement value, String path) {
        if (value == null) {
            if (field.required()) fail(path, "is required");
            return;
        }
        if (field.list()) {
            if (!value.isJsonArray()) fail(path, "must be a list");
            JsonArray array = value.getAsJsonArray();
            if (field.required() && array.isEmpty()) fail(path, "select at least one entry");
            for (int i = 0; i < array.size(); i++) {
                String entryPath = path + " / " + (i + 1);
                JsonElement entry = array.get(i);
                if (field.kind() == Kind.OBJECTS) {
                    if (!entry.isJsonObject()) fail(entryPath, "must be an entry");
                    JsonObject object = entry.getAsJsonObject();
                    for (Field child : field.children()) validate(child, object.get(child.key()), entryPath + " / " + child.label());
                    if (field.key().equals("mobList")) {
                        if (!object.has("entityID") && !object.has("entityModID") && !object.has("entityTag") && !object.has("mobCategory"))
                            fail(entryPath, "choose at least one mob filter");
                        if (object.has("minLight") && object.has("maxLight") && object.get("minLight").getAsInt() > object.get("maxLight").getAsInt())
                            fail(entryPath, "minimum light exceeds maximum light");
                    }
                } else {
                    Kind kind = field.kind() == Kind.IDS ? Kind.ID : field.kind() == Kind.CHOICES ? Kind.CHOICE : Kind.TEXT;
                    validate(new Field(field.key(), field.label(), kind, true, 0, 0, field.choices(), List.of(), ""), entry, entryPath);
                }
            }
            return;
        }
        if (field.kind() == Kind.COMPONENTS) {
            if (!value.isJsonObject()) fail(path, "must contain item components");
            return;
        }
        if (!value.isJsonPrimitive()) fail(path, "has an invalid value");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        String text = primitive.getAsString();
        switch (field.kind()) {
            case ID -> { if (text.isBlank() || Identifier.tryParse(text) == null) fail(path, "enter a valid resource ID"); }
            case TEXT -> {
                if (!primitive.isString() || (text.isBlank() && !field.key().equals("message"))) fail(path, "cannot be empty");
                if (field.key().equals("entityModID") && !text.matches("[a-z0-9_.-]+")) fail(path, "enter a mod namespace");
            }
            case BOOLEAN -> { if (!primitive.isBoolean()) fail(path, "choose Yes or No"); }
            case CHOICE -> {
                String choice = field.key().equals("slot") ? text.toUpperCase(Locale.ROOT) : text;
                if (!field.choices().contains(choice)) fail(path, "choose a supported value");
            }
            case NUMBER -> {
                try {
                    int number = Integer.parseInt(text);
                    if (!primitive.isNumber() || number < field.min() || number > field.max()) throw new NumberFormatException();
                } catch (NumberFormatException error) {
                    fail(path, "enter a whole number from " + field.min() + " to " + field.max());
                }
            }
            default -> { }
        }
    }

    private static void fail(String path, String message) {
        throw new IllegalArgumentException(path + ": " + message);
    }

    private RestrictionForm() {}
}
