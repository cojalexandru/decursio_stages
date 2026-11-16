package com.decursioteam.decursio_stages.persistence;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.*;
import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Server-thread edit transactions: compile first, atomically replace each file, then publish once. */
public final class RestrictionSaveService {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private RestrictionSaveService() {}

    public static String version(Path root) throws IOException {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(Long.toString(RestrictionsData.getRegistry().revision()).getBytes(StandardCharsets.UTF_8));
            if (Files.exists(root)) try (var walk = Files.walk(root)) {
                for (Path path : walk.filter(Files::isRegularFile).filter(RestrictionSaveService::ruleFile).sorted().toList()) {
                    hash.update(root.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
                    hash.update((byte) 0); hash.update(Files.readAllBytes(path)); hash.update((byte) 0);
                }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static boolean ruleFile(Path path) {
        String name = path.getFileName().toString(); return name.endsWith(".json") || name.endsWith(".zip");
    }
    private static void name(String name) throws IOException {
        if (!name.matches("[a-z0-9_\\-]{1,64}")) throw new IOException("Invalid restriction name: use 1–64 lowercase letters, digits, _ or -");
    }
    private static Path owned(Path root, String id) throws IOException {
        var sources = RestrictionsData.getRegistry().sources(id);
        if (ScriptRestrictions.contains(id) || sources.size() != 1 || sources.get(0).contains("!/"))
            throw new IOException("Restriction '" + id + "' is script-owned, archived or has duplicate sources; edit its source directly.");
        Path path = Path.of(sources.get(0)).toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path) || !path.toRealPath().startsWith(root.toRealPath()))
            throw new IOException("Restriction source is missing or outside the restrictions directory: " + path);
        try {
            if (!JsonParser.parseString(Files.readString(path)).equals(RestrictionsData.getRegistry().getRawRestrictionData(id)))
                throw new IOException("Source changed since the last reload: " + path + ". Reload rules before editing.");
        } catch (JsonParseException error) { throw new IOException("Unreadable restriction source: " + path, error); }
        return path;
    }
    public static synchronized void edit(Path directory, String expected, String original, String target, JsonObject value) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        checkVersion(root, expected);
        if (value != null) name(target);
        Map<Path, byte[]> changes = new LinkedHashMap<>();
        Path old = original.isEmpty() ? null : owned(root, original);
        if (!original.equals(target) && RestrictionsData.getRegistry().getRawRestrictions().containsKey(target))
            throw new IOException("Restriction already exists: " + target);
        if (value == null) {
            if (old == null) throw new IOException("Nothing selected to delete");
            changes.put(old, null);
        } else {
            Path dest = old != null && original.equals(target) ? old
                    : old != null ? old.resolveSibling(target + ".json") : root.resolve(target + ".json");
            if (!dest.equals(old) && Files.exists(dest)) throw new IOException("Target file already exists: " + dest);
            JsonObject copy = value.deepCopy();
            // Explicit identity preserves custom names and legacy multi-dot source filenames.
            copy.getAsJsonObject("Restriction Data").addProperty("name", target);
            changes.put(dest, JSON.toJson(copy).getBytes(StandardCharsets.UTF_8));
            if (old != null && !old.equals(dest)) changes.put(old, null);
        }
        commit(root, expected, changes);
    }
    public static void checkVersion(Path root, String expected) throws IOException {
        if (!version(root).equals(expected)) throw new IOException("Conflicting edit: rules or files changed. Reopen /decstages gui and reapply your edits; your draft has been kept.");
    }
    public static synchronized void commit(Path root, String expected, Map<Path, byte[]> changes) throws IOException {
        checkVersion(root, expected);
        var candidate = RestrictionLoader.prepare(root, changes);
        for (var entry : changes.entrySet()) if (entry.getValue() != null) {
            JsonObject rule = JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
            String id = RestrictionNames.resolve(entry.getKey().getFileName().toString(), rule);
            if (candidate.sources(id).size() != 1 || ScriptRestrictions.contains(id))
                throw new IOException("Edit would shadow another source for '" + id + "'; resolve ownership first.");
        }
        // Apply the same ore semantics used by the GUI to command-created transactions too.
        try { candidate.getRestrictions().values().forEach(rule -> rule.getData().getBlockReplacementList().forEach(com.decursioteam.decursio_stages.orestaging.OreStages::validate)); }
        catch (RuntimeException error) { throw new IOException("Invalid restriction: " + error.getMessage(), error); }
        Map<Path, byte[]> before = new LinkedHashMap<>();
        for (Path path : changes.keySet()) {
            Path normalized = path.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) throw new IOException("Refusing path outside restrictions directory: " + normalized);
            before.put(normalized, Files.exists(normalized) ? Files.readAllBytes(normalized) : null);
        }
        List<Path> applied = new ArrayList<>();
        try {
            for (var entry : before.entrySet()) if (entry.getValue() != null)
                atomic(entry.getKey().resolveSibling(entry.getKey().getFileName() + ".bak"), entry.getValue());
            for (var entry : changes.entrySet()) {
                if (entry.getValue() == null) Files.delete(entry.getKey()); else atomic(entry.getKey(), entry.getValue());
                applied.add(entry.getKey());
            }
        } catch (IOException error) {
            Collections.reverse(applied);
            List<String> rollbackErrors = new ArrayList<>();
            for (Path path : applied) try {
                if (before.get(path) == null) Files.deleteIfExists(path); else atomic(path, before.get(path));
            } catch (IOException rollback) {
                error.addSuppressed(rollback);
                rollbackErrors.add(path + ": " + rollback.getMessage());
            }
            String recovery = rollbackErrors.isEmpty() ? "Original files restored. "
                    : "ROLLBACK INCOMPLETE; recover from .bak files before reloading: " + String.join("; ", rollbackErrors) + ". ";
            throw new IOException("Restriction save failed; active rules unchanged. " + recovery + error.getMessage(), error);
        }
        RestrictionsData.publish(candidate);
        RestrictionReloadStatus.success();
        Registry.registerRestrictionsList();
    }
    /** Batch every command edit into one candidate and one publication. */
    public static synchronized void restrict(Path directory, String stage, JsonObject settings, net.minecraft.world.item.ItemStack stack) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        String expected = version(root);
        var registry = RestrictionsData.getRegistry();
        JsonObject item = com.decursioteam.decursio_stages.utils.ResourceUtil.itemStackToJson(stack);
        Map<Path, byte[]> changes = new LinkedHashMap<>();
        boolean matched = false;
        for (var entry : registry.getRawRestrictions().entrySet()) {
            String id = entry.getKey(); JsonObject copy = entry.getValue().deepCopy();
            JsonObject data = copy.getAsJsonObject("Restriction Data");
            JsonObject effective = com.decursioteam.decursio_stages.codec.SettingsCodec.CODEC.encodeStart(
                    com.mojang.serialization.JsonOps.INSTANCE, registry.getRestrictions().get(id).getSettingsCodec())
                    .getOrThrow(false, err -> { throw new IllegalStateException(err); }).getAsJsonObject();
            boolean sameStage = data.get("stage").getAsString().equalsIgnoreCase(stage);
            boolean sameSettings = settings.entrySet().stream().allMatch(e -> e.getValue().equals(effective.get(e.getKey())));
            boolean changed = false;
            if (sameStage && sameSettings) {
                matched = true;
                JsonArray items = array(data, "itemList"), exceptions = array(data, "exceptionList");
                if (!items.contains(item)) { items.add(item.deepCopy()); changed = true; }
                if (exceptions.remove(item)) changed = true;
                // Ownership is checked even if the held item was already present.
                owned(root, id);
            } else if (!sameStage) {
                JsonArray items = array(data, "itemList");
                boolean removed = items.remove(item);
                if (removed || registry.compiled(id).matches(stack, com.decursioteam.decursio_stages.utils.ResourceUtil.CHECK_TYPES.ALL)) {
                    JsonArray exceptions = array(data, "exceptionList");
                    if (!exceptions.contains(item)) { exceptions.add(item.deepCopy()); changed = true; }
                    changed |= removed;
                }
            }
            if (changed) changes.put(owned(root, id), JSON.toJson(copy).getBytes(StandardCharsets.UTF_8));
        }
        if (!matched) {
            String seed = (stage + "_" + settings.get("itemsTitle").getAsString()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
            if (seed.isBlank()) seed = "restriction";
            seed = seed.substring(0, Math.min(54, seed.length()));
            String id = seed; int suffix = 1;
            while (registry.getRawRestrictions().containsKey(id) || Files.exists(root.resolve(id + ".json"))) id = seed + "_" + suffix++;
            JsonObject rule = new JsonObject(), data = new JsonObject();
            data.addProperty("stage", stage); array(data, "itemList").add(item);
            rule.add("Restriction Data", data); rule.add("Settings", settings.deepCopy());
            changes.put(root.resolve(id + ".json"), JSON.toJson(rule).getBytes(StandardCharsets.UTF_8));
        }
        if (!changes.isEmpty()) commit(root, expected, changes);
    }
    private static JsonArray array(JsonObject object, String key) {
        if (!object.has(key)) object.add(key, new JsonArray());
        return object.getAsJsonArray(key);
    }

    private static void atomic(Path path, byte[] bytes) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), ".restriction-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
