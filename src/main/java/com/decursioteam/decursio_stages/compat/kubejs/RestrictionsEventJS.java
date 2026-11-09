package com.decursioteam.decursio_stages.compat.kubejs;

import com.decursioteam.decursio_stages.datagen.Restrictions;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.event.KubeEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RestrictionsEventJS implements KubeEvent {
    private final Map<String, JsonObject> definitions = new LinkedHashMap<>();

    public RestrictionBuilder create(String name, String stage) {
        checkName(name);
        if (definitions.containsKey(name)) throw new IllegalArgumentException("Duplicate script restriction: " + name);
        RestrictionBuilder builder = new RestrictionBuilder(stage);
        definitions.put(name, builder.json());
        return builder;
    }

    /** Full restriction JSON, with the same schema as a config restriction file. */
    public void custom(String name, JsonObject json) {
        checkName(name);
        if (definitions.containsKey(name)) throw new IllegalArgumentException("Duplicate script restriction: " + name);
        definitions.put(name, json.deepCopy());
    }

    private static void checkName(String name) {
        if (name == null || !name.matches("[a-z0-9_\\-]{1,64}"))
            throw new IllegalArgumentException("Restriction names must use 1-64 lowercase letters, digits, _ or -");
    }

    Map<String, JsonObject> validate() {
        definitions.forEach((name, json) -> {
            if (!json.has("Restriction Data") || !json.get("Restriction Data").isJsonObject())
                throw new IllegalArgumentException(name + ": missing Restriction Data object");
            JsonObject data = json.getAsJsonObject("Restriction Data");
            if (!data.has("stage")) throw new IllegalArgumentException(name + ": missing stage");
            data.addProperty("stage", StageBindings.normalizeStage(data.get("stage").getAsString()));
            data.remove("name");
            Restrictions.codec(name).parse(JsonOps.INSTANCE, json)
                    .getOrThrow( error -> { throw new IllegalArgumentException(name + ": " + error); });
        });
        return definitions;
    }
}
