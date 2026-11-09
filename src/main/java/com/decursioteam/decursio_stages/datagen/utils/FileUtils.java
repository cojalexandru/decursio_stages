package com.decursioteam.decursio_stages.datagen.utils;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.google.gson.*;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.decursioteam.decursio_stages.utils.ResourceUtil.getRegistryName;
import static com.decursioteam.decursio_stages.utils.ResourceUtil.isItemExcluded;

public class FileUtils
{

    public static final String JSON = ".json";
    public static final String ZIP = ".zip";
    public static final Path MOD_ROOT = ModList.get().getModFileById(DecursioStages.MOD_ID).getFile().getFilePath();

    public static void streamFilesAndParse(Path directoryPath, BiConsumer<Reader, String> instructions, String errorMessage) {
        try (Stream<Path> zipStream = Files.walk(directoryPath);
             Stream<Path> jsonStream = Files.walk(directoryPath)) {
            zipStream.filter(f -> f.getFileName().toString().endsWith(ZIP)).forEach(path -> addZippedFile(path, instructions));
            jsonStream.filter(f -> f.getFileName().toString().endsWith(JSON)).forEach(path -> addFile(path, instructions));
        } catch (IOException e) {
            DecursioStages.LOGGER.error(errorMessage, e);
        }
    }

    public static void setupDefaultFiles(String dataPath, Path targetPath) {
        if (Files.isRegularFile(MOD_ROOT)) {
            try (FileSystem fileSystem = FileSystems.newFileSystem(MOD_ROOT, ClassLoader.getSystemClassLoader())) {
                Path path = fileSystem.getPath(dataPath);
                if (Files.exists(path)) {
                    copyFiles(path, targetPath);
                }
            } catch (IOException e) {
                DecursioStages.LOGGER.error("Could not load source {}!!", MOD_ROOT);
                e.printStackTrace();
            }
        } else if (Files.isDirectory(MOD_ROOT)) {
            copyFiles(Paths.get(MOD_ROOT.toString(), dataPath), targetPath);
        }
    }

    private static void addFile(Path path, BiConsumer<Reader, String> instructions) {
    File f = path.toFile();
    try {
        parseType(f, instructions);
    } catch (IOException e) {
        DecursioStages.LOGGER.warn("File not found: {}", path);
    }
    }

    private static void addZippedFile(Path file, BiConsumer<Reader, String> instructions) {
        try (ZipFile zf = new ZipFile(file.toString())) {
            zf.stream()
                    .filter(zipEntry -> zipEntry.getName().endsWith(JSON))
                    .forEach(zipEntry -> {
                        try {
                            parseType(zf, zipEntry, instructions);
                        } catch (IOException e) {
                            DecursioStages.LOGGER.error("Could not parse zip entry: {}", zipEntry.getName());
                        }
                    });
        } catch (IOException e) {
            DecursioStages.LOGGER.warn("Could not read Zip File: {}", file.getFileName());
        }
    }

    private static void parseType(File file, BiConsumer<Reader, String> consumer) throws IOException {
        String name = file.getName();
        name = name.substring(0, name.indexOf('.'));

        Reader r = Files.newBufferedReader(file.toPath());

        consumer.accept(r, name);
    }

    private static void parseType(ZipFile zf, ZipEntry zipEntry, BiConsumer<Reader, String> consumer) throws IOException {
        String name = zipEntry.getName();
        name = name.substring(name.lastIndexOf("/") + 1, name.indexOf('.'));

        InputStream input = zf.getInputStream(zipEntry);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

        consumer.accept(reader, name);
    }

    private static void copyFiles(Path source, Path targetPath) {
        try (Stream<Path> sourceStream = Files.walk(source)) {
            sourceStream.filter(f -> f.getFileName().toString().endsWith(JSON))
                    .forEach(path -> {
                        try {
                            Files.copy(path, Paths.get(targetPath.toString(), path.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            DecursioStages.LOGGER.error("Could not copy file: {}, Target: {}", path, targetPath);
                        }
                    });
        } catch (IOException e) {
            DecursioStages.LOGGER.error("Could not stream source files: {}", source);
        }
    }

    public static boolean restrictionExists(String restriction, String stage, String advancedTooltips,
                                            String itemTitle, int pickupDelay, boolean hideInJEI,
                                            boolean canPickup, boolean containerListWhitelist,
                                            boolean checkPlayerInventory, boolean checkPlayerEquipment,
                                            boolean usableItems, boolean usableBlocks, boolean destroyableBlocks) {
        try {

            var restrictionData = RestrictionsData.getRestrictionData(restriction);
            if (restrictionData == null) return false;

            return restrictionData.getData().getStage().equals(stage)
                    && restrictionData.getSettingsCodec().getAdvancedTooltips().equals(advancedTooltips)
                    && restrictionData.getSettingsCodec().getItemTitle().equals(itemTitle)
                    && restrictionData.getSettingsCodec().getPickupDelay() == pickupDelay
                    && restrictionData.getSettingsCodec().getHideInJEI() == hideInJEI
                    && restrictionData.getSettingsCodec().getCanPickup() == canPickup
                    && restrictionData.getSettingsCodec().getContainerListWhitelist() == containerListWhitelist
                    && restrictionData.getSettingsCodec().getCheckPlayerInventory() == checkPlayerInventory
                    && restrictionData.getSettingsCodec().getCheckPlayerEquipment() == checkPlayerEquipment
                    && restrictionData.getSettingsCodec().getUsableItems() == usableItems
                    && restrictionData.getSettingsCodec().getUsableBlocks() == usableBlocks
                    && restrictionData.getSettingsCodec().getDestroyableBlocks() == destroyableBlocks;
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Error checking restriction existence: {}", restriction, e);
            return false;
        }
    }

    public static boolean restrictItem(String stage, String advancedTooltips, String itemTitle, int pickupDelay,
                                       boolean hideInJEI, boolean canPickup, boolean containerListWhitelist,
                                       boolean checkPlayerInventory, boolean checkPlayerEquipment,
                                       boolean usableItems, boolean usableBlocks, boolean destroyableBlocks,
                                       ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            DecursioStages.LOGGER.warn("Attempted to restrict a null or empty item");
            return false;
        }

        JsonObject itemElement = createItemJson(itemStack);
        if (itemElement == null) return false;

        AtomicBoolean anyChanges = new AtomicBoolean(false);

        RestrictionsData.getRegistry().getRawRestrictions().forEach((restriction, jsonFile) -> {
            DecursioStages.LOGGER.info("Checking restriction: {}", restriction);
            DecursioStages.LOGGER.info("Data: {}", jsonFile);
            boolean isMatching = restrictionExists(
                    restriction, stage, advancedTooltips, itemTitle, pickupDelay,
                    hideInJEI, canPickup, containerListWhitelist, checkPlayerInventory,
                    checkPlayerEquipment, usableItems, usableBlocks, destroyableBlocks
            );

            if (isMatching) {
                if (addItemToRestriction(restriction, jsonFile, itemStack, itemElement)) {
                    DecursioStages.LOGGER.info("Restricton matched");
                    anyChanges.set(true);
                }
            } else if (RestrictionsData.getRestrictionData(restriction) != null &&
                    !RestrictionsData.getRestrictionData(restriction).getData().getStage().equals(stage)) {
                if (updateExceptionsList(restriction, jsonFile, itemStack, itemElement)) {
                    anyChanges.set(true);
                }
            }
        });

        return anyChanges.get();
    }

    private static JsonObject createItemJson(ItemStack itemStack) {
        try {
            JsonObject itemElement = new JsonObject();
            itemElement.addProperty("item", getRegistryName(itemStack.getItem()).toString());

            if(itemStack.hasTag()) {
                try {
                    JsonElement nbtJson = Dynamic.convert(
                            NbtOps.INSTANCE,
                            JsonOps.INSTANCE,
                            itemStack.getTag()
                    );
                    if (nbtJson != null) {
                        itemElement.add("nbt", nbtJson);
                    }
                } catch (Exception e) {
                    DecursioStages.LOGGER.error("Error adding NBT data", e);
                }
            }

            return itemElement;
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Failed to create JSON for item: {}", itemStack, e);
            return null;
        }
    }

    private static boolean addItemToRestriction(String restriction, JsonObject jsonFile, ItemStack itemStack, JsonObject itemElement) {
        try {
            boolean changed = false;

            JsonArray itemList = jsonFile.get("Restriction Data").getAsJsonObject().get("itemList").getAsJsonArray();
            DecursioStages.LOGGER.info(jsonFile.toString());
            boolean alreadyExists = false;

            for (int i = 0; i < itemList.size(); i++) {
                if (itemsMatch(itemList.get(i).getAsJsonObject(), itemElement)) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                itemList.add(itemElement);
                DecursioStages.LOGGER.info("Successfully added item {} for stage",
                        itemList);

                changed = true;
            }

            JsonArray exceptionList = jsonFile.get("Restriction Data").getAsJsonObject().get("exceptionList").getAsJsonArray();
            for (int i = 0; i < exceptionList.size(); i++) {
                if (itemsMatch(exceptionList.get(i).getAsJsonObject(), itemElement)) {
                    exceptionList.remove(i);
                    changed = true;
                    break;
                }
            }

            if (changed) {
                saveRestrictionFile(restriction, jsonFile);
            }

            return changed;
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Error adding item to restriction {}: {}", restriction, e.getMessage());
            return false;
        }
    }

    private static boolean updateExceptionsList(String restriction, JsonObject jsonFile, ItemStack itemStack, JsonObject itemElement) {
        try {
            boolean changed = false;

            JsonArray itemList = jsonFile.get("Restriction Data").getAsJsonObject().get("itemList").getAsJsonArray();
            for (int i = 0; i < itemList.size(); i++) {
                if (itemsMatch(itemList.get(i).getAsJsonObject(), itemElement)) {
                    itemList.remove(i);
                    changed = true;
                    break;
                }
            }

            if (isItemExcluded(restriction, itemStack)) {
                JsonArray exceptionList = jsonFile.get("Restriction Data").getAsJsonObject().get("exceptionList").getAsJsonArray();
                boolean alreadyInExceptions = false;

                for (int i = 0; i < exceptionList.size(); i++) {
                    if (itemsMatch(exceptionList.get(i).getAsJsonObject(), itemElement)) {
                        alreadyInExceptions = true;
                        break;
                    }
                }

                if (!alreadyInExceptions) {
                    exceptionList.add(itemElement);
                    changed = true;
                }
            }

            if (changed) {
                saveRestrictionFile(restriction, jsonFile);
            }

            return changed;
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Error updating exceptions list for restriction {}: {}", restriction, e.getMessage());
            return false;
        }
    }

    private static boolean itemsMatch(JsonObject item1, JsonObject item2) {
        if (!item1.has("item") || !item2.has("item")) return false;
        if (!item1.get("item").getAsString().equals(item2.get("item").getAsString())) return false;

        if (item1.has("nbt") && item2.has("nbt")) {
            return item1.get("nbt").toString().equals(item2.get("nbt").toString());
        }

        return !item1.has("nbt") && !item2.has("nbt");
    }

    public static void saveRestrictionFile(String restriction, JsonObject jsonFile) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonOutput = gson.toJson(jsonFile);

            Path filePath = Paths.get(createCustomPath("restrictions") + "/" + restriction + ".json");
            Files.writeString(filePath, jsonOutput, StandardCharsets.UTF_8);

            // Update the cache
            RestrictionsData.getRegistry().cacheRawRestrictionsData(restriction, jsonFile);
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Failed to save restriction file {}: {}", restriction, e.getMessage());
        }
    }

    public static void addRestriction(String stage, String advancedTooltips, String itemTitle, int pickupDelay,
                                      boolean hideInJEI, boolean canPickup, boolean containerListWhitelist,
                                      boolean checkPlayerInventory, boolean checkPlayerEquipment,
                                      boolean usableItems, boolean usableBlocks, boolean destroyableBlocks) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject jsonFile = new JsonObject();
        JsonObject restrictionData = new JsonObject();
        JsonObject settings = new JsonObject();
        JsonArray itemList = new JsonArray();
        JsonArray exceptionList = new JsonArray(); // Add exception list

        jsonFile.add("Restriction Data", restrictionData);
        jsonFile.add("Settings", settings);

        restrictionData.addProperty("stage", stage);
        restrictionData.add("itemList", itemList);
        restrictionData.add("exceptionList", exceptionList); // Add exception list to JSON

        settings.addProperty("advancedTooltips", advancedTooltips);
        settings.addProperty("itemsTitle", itemTitle);
        settings.addProperty("itemsPickupDelay", pickupDelay);
        settings.addProperty("hideInJEI_REI", hideInJEI);
        settings.addProperty("canPickupItems", canPickup);
        settings.addProperty("containerListWhitelist", containerListWhitelist);
        settings.addProperty("dropItemsFromInventory", checkPlayerInventory);
        settings.addProperty("dropArmorFromInventory", checkPlayerEquipment);
        settings.addProperty("canUseItems", usableItems);
        settings.addProperty("canBreakBlocks", destroyableBlocks);
        settings.addProperty("canRightClickBlocks", usableBlocks);

        String normalizedStage = stage.toLowerCase().replace(" ", "_");
        String normalizedTitle = itemTitle.toLowerCase().replace(" ", "_");

        String restrictionId = normalizedStage + "_" + normalizedTitle;

        String jsonOutput = gson.toJson(jsonFile);
        try (PrintWriter out = new PrintWriter(new FileWriter(createCustomPath("restrictions") + "/" + restrictionId + ".json"))) {
            out.write(jsonOutput);
            RestrictionsData.getRegistry().cacheRawRestrictionsData(restrictionId, jsonFile);
            RestrictionsData.getRegistry().regenerateCustomRestrictionData();
            Registry.registerRestrictionsList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static Path createCustomPath(String pathName) {
        Path customPath = Paths.get(FMLPaths.CONFIGDIR.get().toAbsolutePath().toString(), DecursioStages.MOD_ID, pathName);
        createDirectory(customPath, pathName);
        return customPath;
    }
    private static void createDirectory(Path path, String dirName) {
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            DecursioStages.LOGGER.error("failed to create \"{}\" directory", dirName);
        }
    }

}
