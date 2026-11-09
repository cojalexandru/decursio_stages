package com.decursioteam.decursio_stages.compat.ftbquests;

import com.decursioteam.decursio_stages.DecursioStages;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.reward.StageReward;
import dev.ftb.mods.ftbquests.quest.task.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = "decursio_stages", bus = Mod.EventBusSubscriber.Bus.MOD)
public class DecursioQuestIntegration {

    public static TaskType DECURSIO_STAGE;
    public static RewardType DECURSIO_STAGE_REWARD;

    @SubscribeEvent
    public static void registerTaskTypes(RegisterEvent event) {
        if (ModList.get().isLoaded("ftbquests")) {
            try {
                DECURSIO_STAGE = TaskTypes.register(FTBQuestsAPI.rl("decursiostage"), DecursioStageTask::new, () -> Icons.CONTROLLER);
                DECURSIO_STAGE_REWARD = RewardTypes.register(FTBQuestsAPI.rl("decursiostage"), DecursioStageReward::new, () -> Icons.CONTROLLER);
            } catch (NoClassDefFoundError e) {
                //
            }
        }
    }
}