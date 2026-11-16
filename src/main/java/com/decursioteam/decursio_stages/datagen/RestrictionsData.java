package com.decursioteam.decursio_stages.datagen;

import com.decursioteam.decursio_stages.recipes.RecipeRestrictionIndex;
import com.decursioteam.decursio_stages.utils.CompiledRestriction;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import java.util.*;

public class RestrictionsData {

    private static volatile RestrictionsData INSTANCE = new RestrictionsData();
    private Map<String, CompiledRestriction> compiled = new HashMap<>();
    private RecipeRestrictionIndex recipes = RecipeRestrictionIndex.EMPTY;
    public RecipeRestrictionIndex recipes() { return recipes; }
    private final Map<String, List<String>> sources = new LinkedHashMap<>();
    public CompiledRestriction compiled(String name) {
        return compiled.getOrDefault(name, CompiledRestriction.EMPTY);
    }
    private long revision;
    public long revision() { return revision; }
    private final Map<String, JsonObject> RAW_DATA = new LinkedHashMap<>();
    private final Map<String, Restrictions> CUSTOM_DATA = new LinkedHashMap<>();

    /** Build off to the side; published registry instances are never cleared during reload. */
    public static RestrictionsData candidate() { return new RestrictionsData(); }

    public void addDefinition(String name, JsonObject json, String source) {
        if (this == INSTANCE || this == clientSnapshot) throw new IllegalStateException("Definitions must be added to an unpublished candidate");
        try {
            RestrictionSchema.validate(json);
            JsonObject copy = json.deepCopy();
            Restrictions parsed = Restrictions.codec(name).parse(JsonOps.INSTANCE, copy)
                    .getOrThrow(false, error -> { throw new IllegalArgumentException(error); });
            parsed.rawData = copy;
            CompiledRestriction index = new CompiledRestriction(parsed.getData());
            RAW_DATA.put(name, copy);
            CUSTOM_DATA.put(name, parsed);
            compiled.put(name, index);
            recordSource(name, source);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(source + ": " + error.getMessage(), error);
        }
    }

    public static synchronized void publish(RestrictionsData candidate) {
        candidate.compiled = Map.copyOf(candidate.compiled);
        candidate.recipes = new RecipeRestrictionIndex(candidate.CUSTOM_DATA.values());
        candidate.revision = INSTANCE.revision + 1;
        INSTANCE = candidate;
    }

    public void recordSource(String name, String source) {
        sources.computeIfAbsent(name, key -> new ArrayList<>()).add(source);
    }

    public List<String> sources(String name) {
        return List.copyOf(sources.getOrDefault(name, List.of()));
    }

    public String effectiveSource(String name) {
        List<String> list = sources.getOrDefault(name, List.of());
        return list.isEmpty() ? "Runtime definition (source not recorded)" : list.get(list.size() - 1);
    }

    private static volatile RestrictionsData clientSnapshot;

    public static void clearClientSnapshot() { clientSnapshot = null; }

    public static void applyClientSnapshot(Map<String, JsonObject> data) {
        applyClientChanges(data, Set.of(), true);
    }

    /** Parse only changed entries and publish atomically; unchanged parsed rules/indices are reused. */
    public static void applyClientChanges(Map<String, JsonObject> changed, Set<String> removed, boolean full) {
        RestrictionsData previous = clientSnapshot;
        RestrictionsData replacement = new RestrictionsData();
        Map<String, CompiledRestriction> indices = new HashMap<>();
        if (!full && previous != null) {
            replacement.RAW_DATA.putAll(previous.RAW_DATA);
            replacement.CUSTOM_DATA.putAll(previous.CUSTOM_DATA);
            indices.putAll(previous.compiled);
        }
        for (String id : removed) {
            replacement.RAW_DATA.remove(id);
            replacement.CUSTOM_DATA.remove(id);
            indices.remove(id);
        }
        changed.forEach((id, value) -> {
            JsonObject json = value.deepCopy();
            Restrictions rule = Restrictions.codec(id).parse(JsonOps.INSTANCE, json)
                    .getOrThrow(false, error -> { throw new IllegalArgumentException(error); });
            rule.rawData = json;
            replacement.RAW_DATA.put(id, json);
            replacement.CUSTOM_DATA.put(id, rule);
            indices.put(id, new CompiledRestriction(rule.getData()));
        });
        replacement.compiled = Map.copyOf(indices);
        replacement.recipes = new RecipeRestrictionIndex(replacement.CUSTOM_DATA.values());
        replacement.revision = previous == null ? 1 : previous.revision + 1;
        clientSnapshot = replacement;
    }

    public static RestrictionsData getRegistry() {
        if (EffectiveSide.get().isClient() && clientSnapshot != null) return clientSnapshot;
        return INSTANCE;
    }

    public static Restrictions getRestrictionData(String name) {
        return getRegistry().CUSTOM_DATA.getOrDefault(name, Restrictions.DEFAULT);
    }

    public JsonObject getRawRestrictionData(String name) {
        return RAW_DATA.get(name);
    }

    public Map<String, Restrictions> getRestrictions() {
        return Collections.unmodifiableMap(CUSTOM_DATA);
    }

    public Map<String, JsonObject> getRawRestrictions() {
        return Collections.unmodifiableMap(RAW_DATA);
    }
}
