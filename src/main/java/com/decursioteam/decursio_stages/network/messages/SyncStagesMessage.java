package com.decursioteam.decursio_stages.network.messages;

import java.util.Collection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class SyncStagesMessage implements CustomPacketPayload {
    public static final Type<SyncStagesMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decursio_stages", "syncstagesmessage"));
    @Override public Type<SyncStagesMessage> type() { return TYPE; }


    private final String[] stages;

    public SyncStagesMessage(String... stages) {

        this.stages = stages;
    }

    public SyncStagesMessage(Collection<String> stages) {

        this(stages.toArray(new String[0]));
    }

    public String[] getStages () {

        return this.stages;
    }
}
