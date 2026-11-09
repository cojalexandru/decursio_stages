package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

public class UpdateStageEvent extends PlayerEvent {

    private final String stageName;

    public UpdateStageEvent(Player player, String stageName) {

        super(player);
        this.stageName = stageName;
    }

    public String getStageName () {

        return this.stageName;
    }

    @Cancelable
    public static class Add extends UpdateStageEvent {

        public Add(Player player, String stageName) {

            super(player, stageName);
        }
    }

    public static class Added extends UpdateStageEvent {

        public Added(Player player, String stageName) {

            super(player, stageName);
        }
    }

    @Cancelable
    public static class Remove extends UpdateStageEvent {

        public Remove(Player player, String stageName) {

            super(player, stageName);
        }
    }

    public static class Removed extends UpdateStageEvent {

        public Removed(Player player, String stageName) {

            super(player, stageName);
        }
    }


    public static class Cleared extends PlayerEvent {

        private final IStagesData stageData;

        public Cleared(Player player, IStagesData stageData) {

            super(player);

            this.stageData = stageData;
        }

        public IStagesData getStageData () {

            return this.stageData;
        }
    }

    public static class Check extends UpdateStageEvent {

        private final boolean hasStageOriginal;

        private boolean hasStage;

        public Check(Player player, String stageName, boolean hasStage) {

            super(player, stageName);
            this.hasStageOriginal = hasStage;
            this.hasStage = hasStage;
        }

        public boolean hadStageOriginally () {

            return this.hasStageOriginal;
        }

        public boolean hasStage () {

            return this.hasStage;
        }

        public void setHasStage (boolean hasStage) {

            this.hasStage = hasStage;
        }
    }
}
