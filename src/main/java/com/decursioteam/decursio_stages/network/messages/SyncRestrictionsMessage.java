package com.decursioteam.decursio_stages.network.messages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

public record SyncRestrictionsMessage(Map<String, JsonObject> restrictions) implements CustomPacketPayload {
    public static final Type<SyncRestrictionsMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decursio_stages", "syncrestrictionsmessage"));
    @Override public Type<SyncRestrictionsMessage> type() { return TYPE; }

    public SyncRestrictionsMessage {
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        restrictions.forEach((name, json) -> copy.put(name, json.deepCopy()));
        restrictions = Map.copyOf(copy);
    }
    public void encode(FriendlyByteBuf buffer) {
        JsonObject json = new JsonObject();
        restrictions.forEach(json::add);
        JsonPayload.write(buffer, json.toString());
    }
    public static SyncRestrictionsMessage decode(FriendlyByteBuf buffer) {
        Map<String, JsonObject> data = new LinkedHashMap<>();
        JsonParser.parseString(JsonPayload.read(buffer)).getAsJsonObject().entrySet()
                .forEach(e -> data.put(e.getKey(), e.getValue().getAsJsonObject()));
        return new SyncRestrictionsMessage(data);
    }
}
