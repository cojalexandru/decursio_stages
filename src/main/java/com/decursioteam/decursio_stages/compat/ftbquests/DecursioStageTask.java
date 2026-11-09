package com.decursioteam.decursio_stages.compat.ftbquests;

import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.ChatFormatting;
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;

public class DecursioStageTask extends AbstractBooleanTask {
    private String stage = "";

    public DecursioStageTask(long id, Quest quest) {
        super(id, quest);
    }

    public TaskType getType() {
        return DecursioQuestIntegration.DECURSIO_STAGE;
    }

    public void writeData(Json5Object nbt, net.minecraft.core.HolderLookup.Provider registries) {
        super.writeData(nbt, registries);
        nbt.addProperty("stage", this.stage);
    }

    public void readData(Json5Object nbt, net.minecraft.core.HolderLookup.Provider registries) {
        super.readData(nbt, registries);
        this.stage = Json5Util.getString(nbt, "stage").orElse("");
    }

    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(this.stage, 32767);
    }

    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        this.stage = buffer.readUtf(32767);
    }

    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", this.stage, (v) -> this.stage = v, "").setNameKey("ftbquests.task.ftbquests.decursiostage");
    }

    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.ftbquests.decursiostage").append(": ").append(Component.literal(this.stage).withStyle(ChatFormatting.DARK_PURPLE));
    }

    public int autoSubmitOnPlayerTick() {
        return 20;
    }

    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        return hasStage(player, this.stage);
    }
}
