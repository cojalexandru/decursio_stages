package com.decursioteam.decursio_stages.compat.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import java.util.Set;

/** Fluent builder for the existing restriction JSON schema. */
public final class RestrictionBuilder {
    private static final Set<String> BOOLEAN_SETTINGS = Set.of("hideInJEI_REI", "canPickupItems",
            "dropItemsFromInventory", "dropArmorFromInventory", "canUseItems", "canRightClickBlocks",
            "canBreakBlocks", "containerListWhitelist", "applyToFakePlayers");
    private final JsonObject root = new JsonObject();
    private final JsonObject data = new JsonObject();
    private final JsonObject settings = new JsonObject();

    RestrictionBuilder(String stage) {
        data.addProperty("stage", StageBindings.normalizeStage(stage));
        root.add("Restriction Data", data);
        root.add("Settings", settings);
    }
    JsonObject json() { return root; }

    private RestrictionBuilder entry(String list, String key, String value) {
        JsonObject entry = new JsonObject();
        entry.addProperty(key, value);
        array(list).add(entry);
        return this;
    }
    private JsonArray array(String key) {
        if (!data.has(key)) data.add(key, new JsonArray());
        return data.getAsJsonArray(key);
    }
    private static String id(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) throw new IllegalArgumentException("Invalid resource ID: " + id);
        return parsed.toString();
    }
    private static String modId(String mod) {
        if (!mod.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid mod ID: " + mod);
        return mod;
    }

    public RestrictionBuilder item(String id) { return entry("itemList", "item", id(id)); }
    public RestrictionBuilder tag(String id) { array("tagList").add(id(id)); return this; }
    public RestrictionBuilder mod(String mod) { array("modList").add(modId(mod)); return this; }
    public RestrictionBuilder fluid(String id) { return entry("fluidList", "fluid", id(id)); }
    public RestrictionBuilder fluidTag(String id) { return entry("fluidList", "tag", id(id)); }
    public RestrictionBuilder fluidMod(String mod) { return entry("fluidList", "mod", modId(mod)); }
    public RestrictionBuilder exceptItem(String id) { return entry("exceptionList", "item", id(id)); }
    public RestrictionBuilder exceptTag(String id) { return entry("exceptionList", "tag", id(id)); }
    public RestrictionBuilder exceptMod(String mod) { return entry("exceptionList", "mod", modId(mod)); }
    public RestrictionBuilder exceptFluid(String id) { return entry("exceptionList", "fluid", id(id)); }
    public RestrictionBuilder exceptFluidTag(String id) { return entry("exceptionList", "fluidTag", id(id)); }
    public RestrictionBuilder exceptFluidMod(String mod) { return entry("exceptionList", "fluidMod", modId(mod)); }

    public RestrictionBuilder dimension(String dimension) {
        return dimension(dimension, "You don't have access to this dimension");
    }

    public RestrictionBuilder dimension(String dimension, String message) {
        if (message == null) throw new IllegalArgumentException("Dimension message cannot be null");
        JsonObject entry = new JsonObject();
        entry.addProperty("dimension", id(dimension));
        entry.addProperty("message", message);
        array("dimensionList").add(entry);
        return this;
    }

    public RestrictionBuilder container(String menuClass) {
        // These are class names, not menu registry IDs; keep the crafting-grid sentinel too.
        if (menuClass == null || !menuClass.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+"))
            throw new IllegalArgumentException("Container must be a fully qualified menu class name");
        array("containerList").add(menuClass);
        return this;
    }

    public RestrictionBuilder structure(String structure) { return structure(structure, new JsonObject()); }

    public RestrictionBuilder structure(String structure, JsonObject options) {
        requireOptions(options, Set.of("can_use_block", "can_place_block", "can_break_block",
                "can_use_block_list", "can_place_block_list", "can_break_block_list"));
        JsonObject entry = options.deepCopy();
        entry.addProperty("structure", id(structure));
        for (String action : new String[]{"use", "place", "break"}) {
            String flag = "can_" + action + "_block";
            if (!entry.has(flag)) entry.addProperty(flag, false);
            requireBoolean(entry.get(flag), flag);
            String list = flag + "_list";
            if (!entry.has(list)) entry.add(list, new JsonArray());
            normalizeIds(entry, list);
        }
        array("structureList").add(entry);
        return this;
    }

    /** Defaults to blacklisting natural spawns while the nearby player HAS the stage. */
    public RestrictionBuilder mob(String entity) { return mob(entity, new JsonObject()); }
    public RestrictionBuilder mob(String entity, JsonObject options) { return mobEntry("entityID", id(entity), options); }
    public RestrictionBuilder mobMod(String mod, JsonObject options) { return mobEntry("entityModID", modId(mod), options); }
    public RestrictionBuilder mobTag(String tag, JsonObject options) { return mobEntry("entityTag", id(tag), options); }

    private RestrictionBuilder mobEntry(String selector, String value, JsonObject options) {
        requireOptions(options, Set.of("spawnType", "whitelist_blacklist", "minLight", "maxLight",
                "biomes", "health", "effects", "loadout"));
        JsonObject entry = options.deepCopy();
        entry.addProperty(selector, value);
        if (!entry.has("spawnType")) {
            JsonArray types = new JsonArray();
            types.add("NATURAL");
            entry.add("spawnType", types);
        }
        if (!entry.get("spawnType").isJsonArray() || entry.getAsJsonArray("spawnType").isEmpty())
            throw new IllegalArgumentException("spawnType must be a nonempty array");
        for (JsonElement type : entry.getAsJsonArray("spawnType"))
            net.minecraft.world.entity.EntitySpawnReason.valueOf(type.getAsString());
        if (!entry.has("whitelist_blacklist")) entry.addProperty("whitelist_blacklist", "BLACKLIST");
        if (!Set.of("BLACKLIST", "WHITELIST").contains(entry.get("whitelist_blacklist").getAsString()))
            throw new IllegalArgumentException("whitelist_blacklist must be BLACKLIST or WHITELIST");
        if (entry.has("biomes")) normalizeIds(entry, "biomes");
        for (String key : new String[]{"minLight", "maxLight", "health"}) {
            if (!entry.has(key)) continue;
            JsonElement number = entry.get(key);
            if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()
                    || number.getAsDouble() != number.getAsInt()
                    || number.getAsInt() < (key.equals("health") ? 1 : 0)
                    || (!key.equals("health") && number.getAsInt() > 15))
                throw new IllegalArgumentException("Invalid mob option: " + key);
        }
        if (entry.has("minLight") && entry.has("maxLight") && entry.get("minLight").getAsInt() > entry.get("maxLight").getAsInt())
            throw new IllegalArgumentException("minLight cannot exceed maxLight");
        // Validate this entry directly: the outer restriction codec defaults malformed lists to empty.
        com.decursioteam.decursio_stages.mobstaging.MobRestriction.codec()
                .parse(com.mojang.serialization.JsonOps.INSTANCE, entry)
                .getOrThrow( error -> { throw new IllegalArgumentException("Invalid mob rule: " + error); });
        array("mobList").add(entry);
        return this;
    }

    private static void requireOptions(JsonObject options, Set<String> keys) {
        if (options == null) throw new IllegalArgumentException("Options cannot be null");
        for (String key : options.keySet())
            if (!keys.contains(key)) throw new IllegalArgumentException("Unknown option: " + key);
    }

    private static void requireBoolean(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(name + " must be boolean");
    }

    private static void normalizeIds(JsonObject object, String key) {
        if (!object.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array of resource IDs");
        JsonArray normalized = new JsonArray();
        for (JsonElement value : object.getAsJsonArray(key)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException(key + " must contain resource ID strings");
            normalized.add(id(value.getAsString()));
        }
        object.add(key, normalized);
    }

    public RestrictionBuilder setting(String name, JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Invalid setting: " + name);
        var primitive = value.getAsJsonPrimitive();
        if (BOOLEAN_SETTINGS.contains(name)) {
            if (!primitive.isBoolean()) throw new IllegalArgumentException(name + " must be boolean");
        } else if (name.equals("itemsPickupDelay")) {
            if (!primitive.isNumber() || value.getAsDouble() < 0 || value.getAsDouble() != value.getAsInt())
                throw new IllegalArgumentException("itemsPickupDelay must be a nonnegative integer");
        } else if (name.equals("advancedTooltips")) {
            if (!Set.of("ALWAYS", "ADVANCED", "NONE").contains(value.getAsString()))
                throw new IllegalArgumentException("advancedTooltips must be ALWAYS, ADVANCED or NONE");
        } else if (!name.equals("itemsTitle") || !primitive.isString()) {
            throw new IllegalArgumentException("Unknown or invalid setting: " + name);
        }
        settings.add(name, value.deepCopy());
        return this;
    }
}
