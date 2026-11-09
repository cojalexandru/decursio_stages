package com.decursioteam.decursio_stages.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;

/** Loaded only when FTB Quests is installed. */
public final class DecursioQuestIntegration {
    public static TaskType DECURSIO_STAGE;
    public static RewardType DECURSIO_STAGE_REWARD;
    public static void register() {
        DECURSIO_STAGE = TaskTypes.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("ftbquests", "decursiostage"), DecursioStageTask::new, () -> Icons.CONTROLLER);
        DECURSIO_STAGE_REWARD = RewardTypes.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("ftbquests", "decursiostage"), DecursioStageReward::new, () -> Icons.CONTROLLER);
    }
}
