package com.decursioteam.decursio_stages.network.messages;

import java.util.Collection;

public class SyncStagesMessage {

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
