package com.decursioteam.decursio_stages.network.messages;

import com.decursioteam.decursio_stages.datagen.StagesData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.utils.StagesHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;

public class SyncStagesMessage implements CustomPacketPayload {
    public static final Type<SyncStagesMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("decursio_stages", "syncstagesmessage"));
    @Override public Type<SyncStagesMessage> type() { return TYPE; }

    private final String[] stages;

    public SyncStagesMessage(String... stages) {
        this.stages = stages;
    }

    public SyncStagesMessage(Collection<String> stages) {
        this(stages.toArray(new String[0]));
    }

    public String[] getStages() {
        return this.stages;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.stages.length);
        for (final String stageName : this.stages) {
            buffer.writeUtf(stageName, 64);
        }
    }

    public static SyncStagesMessage decode(FriendlyByteBuf buffer) {
        final String[] stageNames = new String[buffer.readInt()];
        for (int i = 0; i < stageNames.length; i++) {
            stageNames[i] = buffer.readUtf(64);
        }
        return new SyncStagesMessage(stageNames);
    }

    public static void handle(SyncStagesMessage message, IPayloadContext context) {
        final IStagesData clientData = new StagesData();
        for (final String stageName : message.stages) {
            clientData.addStage(stageName);
        }
        StagesHandler.setClientData(clientData);
        NeoForge.EVENT_BUS.post(new SyncStagesEvent(clientData));
    }
}
