package com.decursioteam.decursio_stages.datagen;

import com.decursioteam.decursio_stages.DecursioStages;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads and compiles an isolated candidate, then publishes one complete registry reference. */
public final class RestrictionLoader {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private RestrictionLoader() {}

    public static synchronized boolean reload(Path directory) {
        try {
            RestrictionsData.publish(prepare(directory.toAbsolutePath().normalize(), Map.of()));
            RestrictionReloadStatus.success();
            return true;
        } catch (IOException error) {
            DecursioStages.LOGGER.error("Restriction reload rejected; last working rules remain active. {}", error.getMessage());
            return false;
        }
    }

    /** Compile a prospective filesystem without modifying files or publishing any live state. */
    public static RestrictionsData prepare(Path directory, Map<Path, byte[]> changes) throws IOException {
        RestrictionsData candidate = RestrictionsData.candidate();
        List<String> errors = new ArrayList<>();
        try {
            Files.createDirectories(directory);
            List<Path> files;
            try (var walk = Files.walk(directory)) {
                files = new ArrayList<>(walk.filter(Files::isRegularFile)
                        .filter(path -> !changes.containsKey(path.toAbsolutePath().normalize())).toList());
            }
            changes.forEach((path, bytes) -> { if (bytes != null) files.add(path); });
            files.sort(Comparator.naturalOrder());
            // Preserve existing precedence: ZIP entries, then loose JSON, then script definitions.
            for (Path file : files) if (file.toString().endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (entry.isDirectory() || !entry.getName().endsWith(".json")) continue;
                        String source = file.toAbsolutePath().normalize() + "!/" + entry.getName();
                        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                            read(candidate, reader, entry.getName(), source);
                        } catch (Exception error) { errors.add(source + ": " + error.getMessage()); }
                    }
                } catch (Exception error) { errors.add(file + ": " + error.getMessage()); }
            }
            for (Path file : files) if (file.toString().endsWith(".json")) {
                String source = file.toAbsolutePath().normalize().toString();
                try (Reader reader = changes.containsKey(file.toAbsolutePath().normalize())
                        ? new StringReader(new String(changes.get(file.toAbsolutePath().normalize()), StandardCharsets.UTF_8))
                        : Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    read(candidate, reader, file.getFileName().toString(), source);
                } catch (Exception error) { errors.add(source + ": " + error.getMessage()); }
            }
        } catch (Exception error) { errors.add(directory + ": " + error.getMessage()); }
        try { ScriptRestrictions.apply(candidate); }
        catch (RuntimeException error) { errors.add(error.getMessage()); }
        if (!errors.isEmpty()) {
            RestrictionReloadStatus.failure(errors);
            throw new IOException(String.join("; ", errors));
        }
        return candidate;
    }

    private static void read(RestrictionsData candidate, Reader reader, String path, String source) {
        JsonObject json = JSON.fromJson(reader, JsonObject.class);
        if (json == null) throw new IllegalArgumentException("Empty restriction file");
        RestrictionSchema.validate(json);
        String name = RestrictionNames.resolve(path, json);
        if (name.isBlank()) throw new IllegalArgumentException("Restriction name must not be blank");
        candidate.addDefinition(name, json, source);
    }
}
