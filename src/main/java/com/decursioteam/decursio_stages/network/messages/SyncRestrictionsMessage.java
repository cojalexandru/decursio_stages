package com.decursioteam.decursio_stages.network.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable wire snapshot. Compression is performed once, including for broadcasts. */
public final class SyncRestrictionsMessage implements CustomPacketPayload {
    public static final Type<SyncRestrictionsMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("decursio_stages", "syncrestrictionsmessage"));
    private final long baseRevision, revision;
    private final boolean full;
    private final Map<String, JsonObject> restrictions;
    private final Set<String> removed;
    private final byte[] payload;

    public SyncRestrictionsMessage(long baseRevision, long revision, boolean full, Map<String, JsonObject> restrictions, Set<String> removed) {
        this(baseRevision, revision, full, restrictions, removed, null);
    }
    private SyncRestrictionsMessage(long baseRevision, long revision, boolean full, Map<String, JsonObject> restrictions, Set<String> removed, byte[] encoded) {
        if (revision < 0 || (!full && (baseRevision < 0 || revision <= baseRevision))) throw new IllegalArgumentException("Invalid restriction revision");
        if (full && !removed.isEmpty()) throw new IllegalArgumentException("Snapshot cannot contain removals");
        this.baseRevision = baseRevision; this.revision = revision; this.full = full;
        Map<String, JsonObject> copy = new LinkedHashMap<>(); restrictions.forEach((name, json) -> copy.put(name, json.deepCopy()));
        this.restrictions = Collections.unmodifiableMap(copy); this.removed = Set.copyOf(removed);
        if (!Collections.disjoint(copy.keySet(), removed)) throw new IllegalArgumentException("Changed and removed rules overlap");
        if (encoded != null) { payload = encoded; return; }
        JsonObject changes = new JsonObject(); copy.forEach(changes::add);
        JsonArray deleted = new JsonArray(); removed.forEach(deleted::add);
        JsonObject envelope = new JsonObject(); envelope.add("changed", changes); envelope.add("removed", deleted);
        payload = JsonPayload.compress(envelope.toString());
    }
    public long baseRevision() { return baseRevision; }
    public long revision() { return revision; }
    public boolean full() { return full; }
    public Map<String, JsonObject> restrictions() { return restrictions; }
    public Set<String> removed() { return removed; }
    @Override public Type<SyncRestrictionsMessage> type() { return TYPE; }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(full); buffer.writeLong(baseRevision); buffer.writeLong(revision); buffer.writeByteArray(payload);
    }
    public static SyncRestrictionsMessage decode(FriendlyByteBuf buffer) {
        boolean full = buffer.readBoolean(); long base = buffer.readLong(), revision = buffer.readLong();
        byte[] encoded = buffer.readByteArray(JsonPayload.MAX_BYTES);
        var json = JsonParser.parseString(JsonPayload.decompress(encoded)).getAsJsonObject();
        Map<String, JsonObject> rules = new LinkedHashMap<>();
        json.getAsJsonObject("changed").entrySet().forEach(e -> rules.put(e.getKey(), e.getValue().getAsJsonObject()));
        Set<String> removed = new HashSet<>(); json.getAsJsonArray("removed").forEach(e -> removed.add(e.getAsString()));
        return new SyncRestrictionsMessage(base, revision, full, rules, removed, encoded);
    }
}
