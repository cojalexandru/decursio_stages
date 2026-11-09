package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.datagen.StagesData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.network.messages.SyncStagesMessage;
import com.decursioteam.decursio_stages.utils.StagesHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class ClientPacketHandler {

    public static SyncStagesMessage decodeStageMessage(FriendlyByteBuf buffer) {
        final String[] stageNames = new String[buffer.readInt()];

        for (int i = 0; i < stageNames.length; i++) {
            stageNames[i] = buffer.readUtf(64);
        }

        return new SyncStagesMessage(stageNames);
    }

    public static void processSyncStagesMessage(SyncStagesMessage message, IPayloadContext context) {
        final IStagesData clientData = new StagesData();

        for (final String stageName : message.getStages()) {
            clientData.addStage(stageName);
        }

        StagesHandler.setClientData(clientData);

        NeoForge.EVENT_BUS.post(new SyncStagesEvent(clientData));
    }
}