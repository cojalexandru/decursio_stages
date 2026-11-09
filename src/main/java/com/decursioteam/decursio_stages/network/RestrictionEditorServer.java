package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.Restrictions;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.FileUtils;
import com.decursioteam.decursio_stages.network.messages.EditRestrictionMessage;
import com.decursioteam.decursio_stages.network.messages.OpenRestrictEditorMessage;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Server side of the restriction editor. The server owns the restriction files: the client only ever
 * edits a copy and sends the result back here, where it is validated, written and reloaded.
 */
public final class RestrictionEditorServer {
    public static final int PERMISSION_LEVEL = 2;
    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_\\-]{1,64}");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private RestrictionEditorServer() {}

    public static void open(ServerPlayer player, @Nullable String select, @Nullable Component status, boolean error) {
        Map<String, JsonObject> snapshot = new LinkedHashMap<>();
        RestrictionsData.getRegistry().getRawRestrictions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> snapshot.put(e.getKey(), e.getValue().deepCopy()));
        DecursioStages.NETWORK.sendToPlayer(player, new OpenRestrictEditorMessage(snapshot, select, status, error));
    }

    public static void handle(EditRestrictionMessage message, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (player == null) return;
        if (!player.hasPermissions(PERMISSION_LEVEL)) {
            DecursioStages.LOGGER.warn("{} tried to edit restrictions without permission", player.getName().getString());
            return;
        }

        try {
            switch (message.getAction()) {
                case SAVE -> save(player, message);
                case DELETE -> delete(player, message.getName());
            }
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Restriction editor: unexpected error", e);
            open(player, message.getOriginalName().isEmpty() ? null : message.getOriginalName(),
                    Component.translatable("decursio_stages.gui.editor.status.unexpected", e.getMessage()), true);
        }
    }

    private static void save(ServerPlayer player, EditRestrictionMessage message) throws IOException {
        String name = message.getName();
        String original = message.getOriginalName();
        String fallbackSelect = original.isEmpty() ? null : original;
        if (com.decursioteam.decursio_stages.datagen.ScriptRestrictions.contains(name)
                || com.decursioteam.decursio_stages.datagen.ScriptRestrictions.contains(original)) {
            open(player, fallbackSelect, Component.translatable("decursio_stages.gui.editor.status.script_owned"), true);
            return;
        }

        if (!VALID_NAME.matcher(name).matches()) {
            open(player, fallbackSelect, Component.translatable("decursio_stages.gui.editor.status.invalid_name"), true);
            return;
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(message.getJson()).getAsJsonObject();
        } catch (Exception e) {
            open(player, fallbackSelect, Component.translatable("decursio_stages.gui.editor.status.invalid_json"), true);
            return;
        }

        JsonObject data = json.has("Restriction Data") && json.get("Restriction Data").isJsonObject()
                ? json.getAsJsonObject("Restriction Data") : null;
        if (data == null || !data.has("stage") || data.get("stage").getAsString().isBlank()) {
            open(player, fallbackSelect, Component.translatable("decursio_stages.gui.editor.status.no_stage"), true);
            return;
        }
        // The file name is the identity of the restriction; a stale "name" field would make it load under another id.
        data.remove("name");

        Optional<String> parseError = Restrictions.codec(name).parse(JsonOps.INSTANCE, json).error().map(err -> err.message());
        if (parseError.isPresent()) {
            open(player, fallbackSelect, Component.translatable("decursio_stages.gui.editor.status.invalid_data", parseError.get()), true);
            return;
        }

        Path dir = FileUtils.createCustomPath("restrictions");
        boolean renaming = !original.isEmpty() && !original.equals(name);

        Path existingTarget = findFile(dir, name);
        if (existingTarget != null && (original.isEmpty() || renaming)) {
            open(player, fallbackSelect, Component.translatable("decursio_stages.gui.editor.status.exists", name), true);
            return;
        }

        Path originalFile = original.isEmpty() ? null : findFile(dir, original);
        Path target = existingTarget != null ? existingTarget
                : (originalFile != null && renaming ? originalFile.resolveSibling(name + FileUtils.JSON) : dir.resolve(name + FileUtils.JSON));

        String previous = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : null;
        Files.writeString(target, PRETTY.toJson(json), StandardCharsets.UTF_8);

        if (!reload()) {
            // Roll back so one bad save can't break every restriction.
            if (previous != null) Files.writeString(target, previous, StandardCharsets.UTF_8);
            else Files.deleteIfExists(target);
            reload();
            open(player, fallbackSelect, Component.translatable("decursio_stages.gui.editor.status.reload_failed"), true);
            return;
        }

        if (renaming && originalFile != null && !originalFile.equals(target)) {
            Files.deleteIfExists(originalFile);
            reload();
        }

        DecursioStages.LOGGER.info("{} saved restriction '{}' via the editor", player.getName().getString(), name);
        open(player, name, Component.translatable("decursio_stages.gui.editor.status.saved", name), false);
    }

    private static void delete(ServerPlayer player, String name) throws IOException {
        if (com.decursioteam.decursio_stages.datagen.ScriptRestrictions.contains(name)) {
            open(player, name, Component.translatable("decursio_stages.gui.editor.status.script_owned"), true);
            return;
        }
        Path file = findFile(FileUtils.createCustomPath("restrictions"), name);
        if (file == null) {
            open(player, name, Component.translatable("decursio_stages.gui.editor.status.not_found", name), true);
            return;
        }
        Files.delete(file);
        reload();
        DecursioStages.LOGGER.info("{} deleted restriction '{}' via the editor", player.getName().getString(), name);
        open(player, null, Component.translatable("decursio_stages.gui.editor.status.deleted", name), false);
    }

    private static boolean reload() {
        try {
            Registry.setupRestrictions();
            Registry.registerRestrictionsList();
            return true;
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Restriction editor: reload failed", e);
            return false;
        }
    }

    /** Finds the .json file a restriction was loaded from, using the same naming rules as {@link Registry}. */
    @Nullable
    private static Path findFile(Path dir, String name) throws IOException {
        Path direct = dir.resolve(name + FileUtils.JSON);
        if (Files.isRegularFile(direct) && name.equals(idOf(direct))) return direct;
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(FileUtils.JSON))
                    .filter(p -> name.equals(idOf(p)))
                    .findFirst().orElse(null);
        }
    }

    @Nullable
    private static String idOf(Path file) {
        String fileName = file.getFileName().toString();
        String id = fileName.substring(0, fileName.length() - FileUtils.JSON.length());
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("Restriction Data");
            if (data != null && data.has("name")) id = data.get("name").getAsString();
        } catch (Exception ignored) {
            // Unreadable file: fall back to the file name.
        }
        return id.toLowerCase(Locale.ENGLISH).replace(" ", "_");
    }
}
