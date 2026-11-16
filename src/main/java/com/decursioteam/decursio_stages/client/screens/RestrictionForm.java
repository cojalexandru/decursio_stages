package com.decursioteam.decursio_stages.client.screens;

import com.decursioteam.decursio_stages.datagen.RestrictionSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;

import java.util.Arrays;
import java.util.List;

/** Field definitions shared by the form widgets and their validation. */
final class RestrictionForm {
    enum Kind { TEXT, ID, MOD, NUMBER, BOOLEAN, CHOICE, IDS, STRINGS, CHOICES, OBJECTS, COMPONENTS }

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

    private static final List<Field> RECIPE_SELECTOR = List.of(
            field("recipe", "Recipe ID", Kind.ID, false, "Exact recipe ID, e.g. minecraft:diamond_sword. Leave blank to disable this filter."),
            field("mod", "Mod ID", Kind.MOD, false, "Mod namespace, e.g. mekanism. Leave blank to disable this filter."),
            field("tag", "Output item tag", Kind.ID, false, "Output item tag ID, e.g. minecraft:planks. Leave blank to disable this filter."));
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
                    Arrays.stream(MobSpawnType.values()).map(Enum::name).toList(), List.of(), "Select one or more spawn reasons."),
            number("minLight", "Minimum light", false, 0, 15),
            number("maxLight", "Maximum light", false, 0, 15),
            field("biomes", "Biomes", Kind.IDS, false, "Optional biome IDs for this rule."),
            number("health", "Whitelist health (half-hearts)", false, 1, Integer.MAX_VALUE),
            objects("effects", "Whitelist effects", EFFECT), objects("loadout", "Whitelist equipment", LOADOUT));

    static final List<Field> SECTIONS = List.of(
            objects("recipeList", "Recipes", RECIPE_SELECTOR),
            objects("recipeExceptionList", "Recipe exceptions", RECIPE_SELECTOR),
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
        RestrictionSchema.validateField(field.key(), value, field.required(), path);
    }

    private RestrictionForm() {}
}
