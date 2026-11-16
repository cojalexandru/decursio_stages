package com.decursioteam.decursio_stages.network.messages;

import net.minecraft.resources.Identifier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.Set;

public record SyncRecipeVisibilityMessage(Set<Identifier> blocked, Set<Integer> displays) implements CustomPacketPayload {
    public static final Type<SyncRecipeVisibilityMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decursio_stages", "recipe_visibility"));
    public SyncRecipeVisibilityMessage { blocked = Set.copyOf(blocked); displays = Set.copyOf(displays); }
    @Override public Type<SyncRecipeVisibilityMessage> type() { return TYPE; }
    public void encode(FriendlyByteBuf buffer) { buffer.writeCollection(blocked, FriendlyByteBuf::writeIdentifier); buffer.writeCollection(displays, FriendlyByteBuf::writeVarInt); }
    public static SyncRecipeVisibilityMessage decode(FriendlyByteBuf buffer) {
        return new SyncRecipeVisibilityMessage(Set.copyOf(buffer.readList(FriendlyByteBuf::readIdentifier)), Set.copyOf(buffer.readList(FriendlyByteBuf::readVarInt)));
    }
}
