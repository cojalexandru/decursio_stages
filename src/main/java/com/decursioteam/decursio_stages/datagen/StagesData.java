package com.decursioteam.decursio_stages.datagen;

import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class StagesData implements IStagesData {

    public static final String stagesTag = "Stages";
    private final Set<String> unlockedStages = new HashSet<>();

    @Override
    public ArrayList<String> getStages () {

        return new ArrayList<>(this.unlockedStages);
    }

    @Override
    public boolean hasStage (String stage) {

        return this.unlockedStages.contains(stage.toLowerCase());
    }

    @Override
    public void addStage (String stage) {

        this.unlockedStages.add(stage.toLowerCase());
    }

    @Override
    public void removeStage (String stage) {

        this.unlockedStages.remove(stage.toLowerCase());
    }

    @Override
    public void clear () {

        this.unlockedStages.clear();
    }

    @Override
    public void readFromNBT (CompoundTag tag) {
        final ListTag list = tag.getList(stagesTag, 8);
        for (int tagIndex = 0; tagIndex < list.size(); tagIndex++) {
            this.addStage(list.getString(tagIndex));
        }
    }

    @Override
    public CompoundTag writeToNBT () {
        final CompoundTag tag = new CompoundTag();
        final ListTag list = new ListTag();
        for (final String stage : this.unlockedStages) {
            list.add(StringTag.valueOf(stage));
        }
        tag.put(stagesTag, list);
        return tag;
    }

    @Override
    public String toString () {
        return "Player Stages [unlockedStages=" + this.unlockedStages + "]";
    }
}
