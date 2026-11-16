package com.decursioteam.decursio_stages.network.messages;

import net.minecraft.network.FriendlyByteBuf;

/** Requests recovery of the requesting client's restriction snapshot only. */
public record RequestRestrictionsMessage() {
    public void encode(FriendlyByteBuf buf) {}

    public static RequestRestrictionsMessage decode(FriendlyByteBuf buf) {
        return new RequestRestrictionsMessage();
    }
}
