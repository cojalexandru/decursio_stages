package com.decursioteam.decursio_stages.network.messages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record SyncRecipeVisibilityMessage(Set<ResourceLocation> blocked, Set<Integer> displays) implements CustomPacketPayload {
    public static final Type<SyncRecipeVisibilityMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("decursio_stages", "recipe_visibility"));
    public SyncRecipeVisibilityMessage { blocked = Set.copyOf(blocked); displays = Set.copyOf(displays); }
    @Override public Type<SyncRecipeVisibilityMessage> type() { return TYPE; }
    public void encode(FriendlyByteBuf buffer) { buffer.writeCollection(blocked, FriendlyByteBuf::writeResourceLocation); buffer.writeCollection(displays, FriendlyByteBuf::writeVarInt); }
    public static SyncRecipeVisibilityMessage decode(FriendlyByteBuf buffer) {
        return new SyncRecipeVisibilityMessage(Set.copyOf(buffer.readList(FriendlyByteBuf::readResourceLocation)), Set.copyOf(buffer.readList(FriendlyByteBuf::readVarInt)));
    }
}
