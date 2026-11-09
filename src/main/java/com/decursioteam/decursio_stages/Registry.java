package com.decursioteam.decursio_stages;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.FileUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Mod.EventBusSubscriber(modid = DecursioStages.MOD_ID)
public class Registry {

    public static final Gson GSON = new Gson();
    private static final Set<String> RESTRICTIONS = new HashSet<>();
    public static Set<String> getRestrictionsHashSet() {
        return new HashSet<>(RestrictionsData.getRegistry().getRestrictions().keySet());
    }

    public static Set<String> getRestrictions() {
        return getRestrictionsHashSet();
    }

    public static void clearRestrictions(){
        RESTRICTIONS.clear();
    }

    public static void registerRestrictionsList() {
        RestrictionsData.getRegistry().getRestrictions().forEach((name, data) -> registerRestrictions(name));
        com.decursioteam.decursio_stages.network.RestrictionSync.broadcast();
    }

    public static void registerRestrictions(String name) {
        RESTRICTIONS.add(name);
    }

    public static void setupRestrictions() {
        DecursioStages.LOGGER.info("Loading restrictions...");
        Registry.clearRestrictions();
        RestrictionsData.getRegistry().clearRawRestrictionsData();
        RestrictionsData.getRegistry().clearCustomRestrictionData();
        FileUtils.streamFilesAndParse(createCustomPath("restrictions"), Registry::parseRestriction, "Could not stream restrictions!");

        com.decursioteam.decursio_stages.datagen.ScriptRestrictions.apply(RestrictionsData.getRegistry());
        RestrictionsData.getRegistry().regenerateCustomRestrictionData();
    }

    private static void parseRestriction(Reader reader, String name) {
        JsonObject jsonObject = GsonHelper.fromJson(GSON, reader, JsonObject.class);
        name = Codec.STRING.fieldOf("name").orElse(name).codec().fieldOf("Restriction Data").codec().parse(JsonOps.INSTANCE, jsonObject).get().orThrow();
        RestrictionsData.getRegistry().cacheRawRestrictionsData(name.toLowerCase(Locale.ENGLISH).replace(" ", "_"), jsonObject);
    }

    private static Path createCustomPath(String pathName) {
        Path customPath = Paths.get(FMLPaths.CONFIGDIR.get().toAbsolutePath().toString(), DecursioStages.MOD_ID, pathName);
        createDirectory(customPath, pathName);
        return customPath;
    }


    private static void createDirectory(Path path, String dirName) {
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException ignored) { //ignored
        } catch (IOException e) {
            DecursioStages.LOGGER.error("failed to create \"{}\" directory", dirName);
        }
    }
}
