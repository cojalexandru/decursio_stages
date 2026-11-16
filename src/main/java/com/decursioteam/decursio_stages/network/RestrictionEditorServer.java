package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.network.messages.EditRestrictionMessage;
import com.decursioteam.decursio_stages.network.messages.OpenRestrictEditorMessage;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server side of the restriction editor. The server owns the restriction files: the client only ever
 * edits a copy and sends the result back here, where it is validated, written and reloaded.
 */
public final class RestrictionEditorServer {
    public static final int PERMISSION_LEVEL = 2;
    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_\\-]{1,64}");

    private RestrictionEditorServer() {}

    public static void open(ServerPlayer player, @Nullable String select, @Nullable Component status, boolean error) {
        String token;
        try { token = com.decursioteam.decursio_stages.persistence.RestrictionSaveService.version(directory()); }
        catch (IOException failure) {
            DecursioStages.LOGGER.error("Cannot read restriction sources", failure);
            player.sendSystemMessage(Component.literal("Cannot open restriction editor: " + failure.getMessage()));
            return;
        }
        Map<String, JsonObject> snapshot = new LinkedHashMap<>();
        RestrictionsData.getRegistry().getRawRestrictions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> snapshot.put(e.getKey(), e.getValue().deepCopy()));
        DecursioStages.NETWORK.sendToPlayer(player, new OpenRestrictEditorMessage(snapshot, select, status, error, token,
                player.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .keySet().stream().map(Object::toString).sorted().toList(),
                player.server.getRecipeManager().getRecipes().stream().map(recipe -> recipe.id().toString()).sorted().toList()));
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
                case DELETE -> delete(player, message);
            }
        } catch (IOException e) {
            DecursioStages.LOGGER.warn("Restriction edit rejected for {}: {}", player.getName().getString(), e.getMessage());
            open(player, message.getOriginalName().isEmpty() ? null : message.getOriginalName(),
                    Component.translatable("decursio_stages.gui.editor.status.save_failed", e.getMessage()), true);
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Restriction editor: unexpected error", e);
            open(player, message.getOriginalName().isEmpty() ? null : message.getOriginalName(),
                    Component.translatable("decursio_stages.gui.editor.status.unexpected", e.getMessage()), true);
        }
    }

    private static java.nio.file.Path directory() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(DecursioStages.MOD_ID).resolve("restrictions");
    }

    private static void save(ServerPlayer player, EditRestrictionMessage message) throws IOException {
        com.decursioteam.decursio_stages.persistence.RestrictionSaveService.edit(directory(), message.getVersion(),
                message.getOriginalName(), message.getName(), JsonParser.parseString(message.getJson()).getAsJsonObject());
        open(player, message.getName(), Component.translatable("decursio_stages.gui.editor.status.saved", message.getName()), false);
    }

    private static void delete(ServerPlayer player, EditRestrictionMessage message) throws IOException {
        com.decursioteam.decursio_stages.persistence.RestrictionSaveService.edit(directory(), message.getVersion(),
                message.getName(), message.getName(), null);
        open(player, null, Component.translatable("decursio_stages.gui.editor.status.deleted", message.getName()), false);
    }
}
