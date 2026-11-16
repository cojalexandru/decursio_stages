package com.decursioteam.decursio_stages.network.messages;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
/** Requests recovery of the requesting client's restriction snapshot only. */
public record RequestRestrictionsMessage() implements CustomPacketPayload {
    public static final Type<RequestRestrictionsMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decursio_stages", "requestrestrictions"));
    @Override public Type<RequestRestrictionsMessage> type() { return TYPE; }
}
