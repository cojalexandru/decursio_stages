package com.decursioteam.decursio_stages.network.messages;

import net.minecraft.network.FriendlyByteBuf;
import java.util.Set;

public record SyncRecipeVisibilityMessage(Set<net.minecraft.resources.ResourceLocation> blocked, Set<Integer> displays) {
    public SyncRecipeVisibilityMessage { blocked = Set.copyOf(blocked); displays = Set.copyOf(displays); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(blocked, FriendlyByteBuf::writeResourceLocation);
        buffer.writeCollection(displays, FriendlyByteBuf::writeVarInt);
    }

    public static SyncRecipeVisibilityMessage decode(FriendlyByteBuf buffer) {
        return new SyncRecipeVisibilityMessage(
                Set.copyOf(buffer.readList(FriendlyByteBuf::readResourceLocation)),
                Set.copyOf(buffer.readList(FriendlyByteBuf::readVarInt)));
    }
}
