package com.decursioteam.decursio_stages.network.messages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server. Asks the server to save or delete a restriction file.
 * The server re-checks permissions and validates everything before touching disk.
 */
public class EditRestrictionMessage implements CustomPacketPayload {
    public static final Type<EditRestrictionMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decursio_stages", "editrestrictionmessage"));
    @Override public Type<EditRestrictionMessage> type() { return TYPE; }

    public enum Action { SAVE, DELETE }

    private final Action action;
    private final String name;
    private final String originalName;
    private final String json;

    /**
     * @param name         target restriction name (file name without extension)
     * @param originalName name the restriction had when loaded, or empty for a new restriction
     * @param json         full restriction JSON (ignored for DELETE)
     */
    public EditRestrictionMessage(Action action, String name, String originalName, String json) {
        this.action = action;
        this.name = name;
        this.originalName = originalName;
        this.json = json;
    }

    public static EditRestrictionMessage decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String name = buf.readUtf(128);
        String originalName = buf.readUtf(128);
        String json = JsonPayload.read(buf);
        return new EditRestrictionMessage(action, name, originalName, json);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(name, 128);
        buf.writeUtf(originalName, 128);
        JsonPayload.write(buf, json);
    }

    /** Rough wire size, so the client can refuse to send something the server would drop. */
    public int estimatedSize() {
        return JsonPayload.compress(json).length + name.length() * 3 + originalName.length() * 3 + 16;
    }

    public Action getAction() { return action; }
    public String getName() { return name; }
    public String getOriginalName() { return originalName; }
    public String getJson() { return json; }
}
