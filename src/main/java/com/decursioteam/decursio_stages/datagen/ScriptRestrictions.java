package com.decursioteam.decursio_stages.datagen;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** A complete replacement on each successful script reload; never writes config files. */
public final class ScriptRestrictions {
    private static volatile Map<String, JsonObject> definitions = Map.of();

    private ScriptRestrictions() {}

    public static void replace(Map<String, JsonObject> replacement) {
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        replacement.forEach((name, json) -> copy.put(name, json.deepCopy()));
        definitions = Map.copyOf(copy);
    }

    public static boolean contains(String name) { return definitions.containsKey(name); }

    public static void apply(RestrictionsData registry) {
        definitions.forEach((name, json) -> registry.cacheRawRestrictionsData(name, json.deepCopy()));
    }

    public static void clear() { definitions = Map.of(); }
}
