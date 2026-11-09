package com.decursioteam.decursio_stages.datagen;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.utils.IRestrictionsData;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import java.util.*;

public class RestrictionsData implements IRestrictionsData {

    private static final RestrictionsData INSTANCE = new RestrictionsData();
    private final Map<String, JsonObject> RAW_DATA = new LinkedHashMap<>();
    private final Map<String, Restrictions> CUSTOM_DATA = new LinkedHashMap<>();


    private static volatile RestrictionsData clientSnapshot;

    public static void clearClientSnapshot() { clientSnapshot = null; }

    public static void applyClientSnapshot(Map<String, JsonObject> data) {
        RestrictionsData replacement = new RestrictionsData();
        data.forEach((key, value) -> replacement.RAW_DATA.put(key, value.deepCopy()));
        replacement.regenerateCustomRestrictionData();
        clientSnapshot = replacement;
    }

    public static RestrictionsData getRegistry() {
        if (net.minecraftforge.fml.util.thread.EffectiveSide.get().isClient() && clientSnapshot != null) return clientSnapshot;
        return INSTANCE;
    }

    public static Restrictions getRestrictionData(String name) {
        return getRegistry().CUSTOM_DATA.getOrDefault(name, Restrictions.DEFAULT);
    }

    public void regenerateCustomRestrictionData() {
        RAW_DATA.forEach((s, jsonObject) -> CUSTOM_DATA.compute(s, (s1, restrictionCodec) ->
                Restrictions.codec(s).parse(JsonOps.INSTANCE, jsonObject)
                        .getOrThrow(false, s2 -> DecursioStages.LOGGER.error("Couldn't create data for {} stage!", s))));
    }

    public JsonObject getRawORestrictionsData(String name) {
        return RAW_DATA.get(name);
    }

    public void clearCustomRestrictionData() {
        CUSTOM_DATA.clear();
    }

    @Override
    public Map<String, Restrictions> getRestrictions() {
        return Collections.unmodifiableMap(CUSTOM_DATA);
    }

    @Override
    public Set<Restrictions> getSetOfRestrictions() {
        return Collections.unmodifiableSet(new HashSet<>(CUSTOM_DATA.values()));
    }

    @Override
    public Map<String, JsonObject> getRawRestrictions() {
        return Collections.unmodifiableMap(RAW_DATA);
    }

    public void clearRawRestrictionsData()
    {
        RAW_DATA.clear();
    }

    public void cacheRawRestrictionsData(String name, JsonObject restrictionCodec) {
        try {
            if(RAW_DATA.containsKey(name.toLowerCase(Locale.ENGLISH))) {
                RAW_DATA.computeIfPresent(name.toLowerCase(Locale.ENGLISH).replace(" ", "_"), (s, oldVal) -> Objects.requireNonNull(restrictionCodec));
            } else RAW_DATA.computeIfAbsent(name.toLowerCase(Locale.ENGLISH).replace(" ", "_"), s -> Objects.requireNonNull(restrictionCodec));
        } catch (IllegalArgumentException e){
            DecursioStages.LOGGER.error("There is a error with the " + name + " restriction file");
            DecursioStages.LOGGER.error(e);
        }
    }
}
