package com.decursioteam.decursio_stages;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.google.gson.Gson;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class Registry {

    public static final Gson GSON = new Gson();
    public static Set<String> getRestrictionsHashSet() {
        return new HashSet<>(RestrictionsData.getRegistry().getRestrictions().keySet());
    }

    public static Set<String> getRestrictions() {
        return getRestrictionsHashSet();
    }

    public static void registerRestrictionsList() {
        com.decursioteam.decursio_stages.network.RestrictionSync.broadcast();
    }

    public static boolean setupRestrictions() {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(DecursioStages.MOD_ID).resolve("restrictions");
        return com.decursioteam.decursio_stages.datagen.RestrictionLoader.reload(directory);
    }
}
