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
    public NetworkUtil(String channelName, String version) { this.version = version; }

    public void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(version);
        registrar.playToClient(SyncStagesMessage.TYPE,
                StreamCodec.of((buf, msg) -> ServerPacketHandler.encodeStageMessage(msg, buf), ClientPacketHandler::decodeStageMessage),
                ClientPacketHandler::processSyncStagesMessage);
        registrar.playToClient(OpenRestrictEditorMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), OpenRestrictEditorMessage::decode),
                MessageHandlers::handleOpenRestrictEditor);
        registrar.playToServer(EditRestrictionMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), EditRestrictionMessage::decode),
                RestrictionEditorServer::handle);
        registrar.playToClient(SyncRestrictionsMessage.TYPE,
                StreamCodec.of((buf, msg) -> msg.encode(buf), SyncRestrictionsMessage::decode),
                (msg, context) -> com.decursioteam.decursio_stages.client.ClientRestrictionSync.apply(msg));
    }
    public void sendToServer(CustomPacketPayload message) { PacketDistributor.sendToServer(message); }
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload message) { PacketDistributor.sendToPlayer(player, message); }
    public void sendToAllPlayers(CustomPacketPayload message) { PacketDistributor.sendToAllPlayers(message); }
}
