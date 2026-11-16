package com.decursioteam.decursio_stages.datagen;

import com.google.gson.JsonObject;

import java.util.Locale;

/** Shared identity rules for the loader and file editor, preserving legacy first-dot filenames. */
public final class RestrictionNames {
    private RestrictionNames() {}

    public static String fileName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int extension = name.indexOf('.');
        return extension < 0 ? name : name.substring(0, extension);
    }

    public static String resolve(String path, JsonObject json) {
        String name = fileName(path);
        JsonObject data = json.getAsJsonObject("Restriction Data");
        if (data != null && data.has("name")) name = data.get("name").getAsString();
        return name.toLowerCase(Locale.ROOT).replace(" ", "_");
    }
}
