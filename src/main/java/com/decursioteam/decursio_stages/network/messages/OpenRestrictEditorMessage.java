package com.decursioteam.decursio_stages.network.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> client. Opens the restriction editor (or refreshes it if it is already open)
 * with a snapshot of every restriction the server currently knows about.
 */
public class OpenRestrictEditorMessage implements CustomPacketPayload {
    public static final Type<OpenRestrictEditorMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("decursio_stages", "openrestricteditormessage"));
    @Override public Type<OpenRestrictEditorMessage> type() { return TYPE; }

    private final Map<String, JsonObject> restrictions;
    @Nullable private final String select;
    @Nullable private final Component status;
    private final boolean error;
    private final String version;
    private final List<String> structureIds;
    private final List<String> recipeIds;

    public OpenRestrictEditorMessage(Map<String, JsonObject> restrictions, @Nullable String select, @Nullable Component status, boolean error, String version, List<String> structureIds, List<String> recipeIds) {
        this.recipeIds = List.copyOf(recipeIds);
        this.version = version;
        this.structureIds = List.copyOf(structureIds);
        this.restrictions = restrictions;
        this.select = select;
        this.status = status;
        this.error = error;
    }

    public static OpenRestrictEditorMessage decode(RegistryFriendlyByteBuf buf) {
        JsonObject all = JsonParser.parseString(JsonPayload.read(buf)).getAsJsonObject();
        Map<String, JsonObject> map = new LinkedHashMap<>();
        all.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsJsonObject()));
        String select = buf.readBoolean() ? buf.readUtf() : null;
        Component status = buf.readBoolean() ? ComponentSerialization.STREAM_CODEC.decode(buf) : null;
        boolean error = buf.readBoolean();
        String version = buf.readUtf(64);
        JsonArray ids = JsonParser.parseString(JsonPayload.read(buf)).getAsJsonArray();
        List<String> structures = new ArrayList<>();
        ids.forEach(id -> structures.add(id.getAsString()));
        List<String> recipes = new ArrayList<>();
        JsonParser.parseString(JsonPayload.read(buf)).getAsJsonArray().forEach(id -> recipes.add(id.getAsString()));
        return new OpenRestrictEditorMessage(map, select, status, error, version, structures, recipes);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        JsonObject all = new JsonObject();
        restrictions.forEach(all::add);
        JsonPayload.write(buf, all.toString());
        buf.writeBoolean(select != null);
        if (select != null) buf.writeUtf(select);
        buf.writeBoolean(status != null);
        if (status != null) ComponentSerialization.STREAM_CODEC.encode(buf, status);
        buf.writeBoolean(error);
        buf.writeUtf(version, 64);
        JsonArray ids = new JsonArray();
        structureIds.forEach(ids::add);
        JsonPayload.write(buf, ids.toString());
        JsonArray recipes = new JsonArray();
        recipeIds.forEach(recipes::add);
        JsonPayload.write(buf, recipes.toString());
    }

    public Map<String, JsonObject> getRestrictions() { return restrictions; }
    @Nullable public String getSelect() { return select; }
    @Nullable public Component getStatus() { return status; }
    public List<String> getRecipeIds() { return recipeIds; }
    public List<String> getStructureIds() { return structureIds; }
    public String getVersion() { return version; }
    public boolean isError() { return error; }
}
