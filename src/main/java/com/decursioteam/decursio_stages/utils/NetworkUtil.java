package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.network.*;
import com.decursioteam.decursio_stages.network.messages.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkUtil {
    private final String version;
    public NetworkUtil(String version) { this.version = version; }

    public void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(version);
        registrar.playToClient(SyncRecipeVisibilityMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), SyncRecipeVisibilityMessage::decode),
                (msg, context) -> com.decursioteam.decursio_stages.client.ClientRecipeVisibility.apply(msg.blocked(), msg.displays()));
        registrar.playToClient(SyncStagesMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), SyncStagesMessage::decode),
                SyncStagesMessage::handle);
        registrar.playToClient(OpenRestrictEditorMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), OpenRestrictEditorMessage::decode),
                (msg, context) -> com.decursioteam.decursio_stages.client.screens.RestrictEditorClient.handle(msg));
        registrar.playToServer(EditRestrictionMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), EditRestrictionMessage::decode),
                RestrictionEditorServer::handle);
        registrar.playToServer(RequestRestrictionsMessage.TYPE,
                StreamCodec.of((buf, msg) -> {}, buf -> new RequestRestrictionsMessage()),
                (msg, context) -> {
                    if (context.player() instanceof ServerPlayer player) RestrictionSync.recover(player);
                });
        registrar.playToClient(SyncRestrictionsMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), SyncRestrictionsMessage::decode),
                (msg, context) -> com.decursioteam.decursio_stages.client.ClientRestrictionSync.apply(msg));
    }
    public void sendToServer(CustomPacketPayload message) { net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(message); }
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload message) { PacketDistributor.sendToPlayer(player, message); }
    public void sendToAllPlayers(CustomPacketPayload message) { PacketDistributor.sendToAllPlayers(message); }
}
