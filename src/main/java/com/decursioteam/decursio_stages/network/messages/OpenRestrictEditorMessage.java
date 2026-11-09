package com.decursioteam.decursio_stages.network.messages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server -> client. Opens the restriction editor (or refreshes it if it is already open)
 * with a snapshot of every restriction the server currently knows about.
 */
public class OpenRestrictEditorMessage {
    private final Map<String, JsonObject> restrictions;
    @Nullable private final String select;
    @Nullable private final Component status;
    private final boolean error;

    public OpenRestrictEditorMessage(Map<String, JsonObject> restrictions, @Nullable String select, @Nullable Component status, boolean error) {
        this.restrictions = restrictions;
        this.select = select;
        this.status = status;
        this.error = error;
    }

    public static OpenRestrictEditorMessage decode(FriendlyByteBuf buf) {
        JsonObject all = JsonParser.parseString(JsonPayload.read(buf)).getAsJsonObject();
        Map<String, JsonObject> map = new LinkedHashMap<>();
        all.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsJsonObject()));
        String select = buf.readBoolean() ? buf.readUtf() : null;
        Component status = buf.readBoolean() ? buf.readComponent() : null;
        boolean error = buf.readBoolean();
        return new OpenRestrictEditorMessage(map, select, status, error);
    }

    public void encode(FriendlyByteBuf buf) {
        JsonObject all = new JsonObject();
        restrictions.forEach(all::add);
        JsonPayload.write(buf, all.toString());
        buf.writeBoolean(select != null);
        if (select != null) buf.writeUtf(select);
        buf.writeBoolean(status != null);
        if (status != null) buf.writeComponent(status);
        buf.writeBoolean(error);
    }

    public Map<String, JsonObject> getRestrictions() { return restrictions; }
    @Nullable public String getSelect() { return select; }
    @Nullable public Component getStatus() { return status; }
    public boolean isError() { return error; }
}
