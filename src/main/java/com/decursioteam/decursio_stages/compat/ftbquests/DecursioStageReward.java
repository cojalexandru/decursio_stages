package com.decursioteam.decursio_stages.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import static com.decursioteam.decursio_stages.utils.StageUtil.addStage;
import static com.decursioteam.decursio_stages.utils.StageUtil.removeStage;

public class DecursioStageReward extends Reward {
    private String stage = "";
    private boolean remove = false;

    public DecursioStageReward(long id, Quest quest) {
        super(id, quest);
        this.autoclaim = RewardAutoClaim.INVISIBLE;
    }

    public RewardType getType() {
        return DecursioQuestIntegration.DECURSIO_STAGE_REWARD;
    }

    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("stage", this.stage);
        if (this.remove) {
            nbt.putBoolean("remove", true);
        }

    }

    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        this.stage = nbt.getString("stage");
        this.remove = nbt.getBoolean("remove");
    }

    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(this.stage, 32767);
        buffer.writeBoolean(this.remove);
    }

    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        this.stage = buffer.readUtf(32767);
        this.remove = buffer.readBoolean();
    }

    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", this.stage, (v) -> this.stage = v, "").setNameKey("ftbquests.reward.ftbquests.decursiostage");
        config.addBool("remove", this.remove, (v) -> this.remove = v, false);
    }

    public void claim(ServerPlayer player, boolean notify) {
        if (this.remove) {
            removeStage(player, this.stage);
        } else {
            addStage(player, this.stage);
        }

        if (notify) {
            if (this.remove) {
                player.sendSystemMessage(Component.translatable("decursio_stages.commands.removestage.success.target", new Object[]{this.stage}), true);
            } else {
                player.sendSystemMessage(Component.translatable("decursio_stages.commands.addstage.success.target", new Object[]{this.stage}), true);
            }
        }

    }

    @OnlyIn(Dist.CLIENT)
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.reward.ftbquests.decursiostage").append(": ").append(Component.literal(this.stage).withStyle(ChatFormatting.DARK_PURPLE));
    }

    public boolean ignoreRewardBlocking() {
        return true;
    }

    protected boolean isIgnoreRewardBlockingHardcoded() {
        return true;
    }
}

