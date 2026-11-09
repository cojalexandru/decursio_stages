package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.network.messages.SyncStagesMessage;
import net.minecraft.network.FriendlyByteBuf;

public class ServerPacketHandler {

    public static void encodeStageMessage(SyncStagesMessage packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.getStages().length);

        for (final String stageName : packet.getStages()) {
            buffer.writeUtf(stageName, 64);
        }
    }
}