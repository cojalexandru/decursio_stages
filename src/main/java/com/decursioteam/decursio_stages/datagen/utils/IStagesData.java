package com.decursioteam.decursio_stages.datagen.utils;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nonnull;
import java.util.ArrayList;

public interface IStagesData {

    ArrayList<String> getStages();

    boolean hasStage (@Nonnull String stage);

    void addStage (@Nonnull String stage);

    void removeStage (@Nonnull String stage);

    void clear ();

    void readFromNBT (CompoundTag tag);

    CompoundTag writeToNBT ();
}
