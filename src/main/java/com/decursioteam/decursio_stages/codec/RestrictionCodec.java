package com.decursioteam.decursio_stages.codec;

import com.decursioteam.decursio_stages.mobstaging.MobRestriction;
import com.decursioteam.decursio_stages.restrictions.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class RestrictionCodec {

    public static final RestrictionCodec DEFAULT = new RestrictionCodec("error");
    protected final List<ItemRestriction> itemList;
    protected final List<DimensionRestriction> dimensionList;
    protected final List<StructureRestriction> structureList;
    protected final List<ResourceLocation> tagList;
    protected final List<FluidRestriction> fluidList;
    protected final List<MobRestriction> mobList;
    protected final List<String> modList;
    protected final List<String> containerList;
    protected final List<ItemExclusion> exceptionList;
    protected String name;
    protected String stage;

    private RestrictionCodec(String name, String stage, List<ItemRestriction> itemList, List<FluidRestriction> fluidList, List<ResourceLocation> tagList, List<MobRestriction> mobList, List<DimensionRestriction> dimensionList,  List<StructureRestriction> structureList, List<String> modList, List<String> containerList, List<ItemExclusion> exceptionList) {
        this.name = name;
        this.stage = stage;
        this.itemList = itemList;
        this.fluidList = fluidList;
        this.tagList = tagList;
        this.mobList = mobList;
        this.dimensionList = dimensionList;
        this.structureList = structureList;
        this.modList = modList;
        this.containerList = containerList;
        this.exceptionList = exceptionList;
    }

    private RestrictionCodec(String name) {
        this.name = name;
        this.itemList = new ArrayList<>();
        this.fluidList = new ArrayList<>();
        this.dimensionList = new ArrayList<>();
        this.tagList = new ArrayList<>();
        this.mobList = new ArrayList<>();
        this.structureList = new ArrayList<>();
        this.modList = new ArrayList<>();
        this.containerList = new ArrayList<>();
        this.exceptionList = new ArrayList<>();
    }

    public static Codec<RestrictionCodec> codec(String name) {
        return RecordCodecBuilder.create(instance -> instance.group(
                MapCodec.of(Encoder.empty(), Decoder.unit(() -> name)).forGetter(RestrictionCodec::getName),
                Codec.STRING.fieldOf("stage").orElse("").forGetter(RestrictionCodec::getStage),
                ItemRestriction.codec().listOf().fieldOf("itemList").orElse(List.of()).forGetter(RestrictionCodec::getItemList),
                FluidRestriction.codec().listOf().fieldOf("fluidList").orElse(List.of()).forGetter(RestrictionCodec::getFluidList),
                ResourceLocation.CODEC.listOf().fieldOf("tagList").orElse(List.of()).forGetter(RestrictionCodec::getTagList),
                MobRestriction.codec().listOf().fieldOf("mobList").orElse(List.of()).forGetter(RestrictionCodec::getMobList),
                DimensionRestriction.codec().listOf().fieldOf("dimensionList").orElse(List.of()).forGetter(RestrictionCodec::getDimensionList),
                StructureRestriction.codec().listOf().fieldOf("structureList").orElse(List.of()).forGetter(RestrictionCodec::getStructureList),
                Codec.STRING.listOf().fieldOf("modList").orElse(List.of()).forGetter(RestrictionCodec::getModList),
                Codec.STRING.listOf().fieldOf("containerList").orElse(List.of()).forGetter(RestrictionCodec::getContainerList),
                ItemExclusion.codec().listOf().fieldOf("exceptionList").orElse(List.of()).forGetter(RestrictionCodec::getExceptionList)
        ).apply(instance, RestrictionCodec::new));
    }

    public List<FluidRestriction> getFluidList() {
        return fluidList;
    }

    public List<MobRestriction> getMobList() {
        return mobList;
    }

    public List<ItemRestriction> getItemList() {
        return itemList;
    }

    public String getStage() {
        return stage;
    }

    public List<ResourceLocation> getTagList() {
        return tagList;
    }

    public List<String> getModList() {
        return modList;
    }

    public List<DimensionRestriction> getDimensionList() {
        return dimensionList;
    }

    public List<StructureRestriction> getStructureList() {
        return structureList;
    }

    public List<String> getContainerList() {
        return containerList;
    }

    public List<ItemExclusion> getExceptionList() {
        return exceptionList;
    }


    public String getName() {
        return name;
    }

    public RestrictionCodec toImmutable() {
        return this;
    }

    public static class Mutable extends RestrictionCodec {

        public Mutable(String name, String stage, List<ItemRestriction> itemList, List<FluidRestriction> fluidList, List<ResourceLocation> tagList, List<MobRestriction> mobList, List<DimensionRestriction> dimensionList, List<StructureRestriction> structureList, List<String> modList, List<String> containerList, List<ItemExclusion> exceptionList) {
            super(name, stage, itemList, fluidList, tagList, mobList, dimensionList, structureList, modList, containerList, exceptionList);
        }

        public Mutable(String name) {
            super(name);
        }

        public Mutable setName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public RestrictionCodec toImmutable() {
            return new RestrictionCodec(this.name, this.stage, this.itemList, this.fluidList, tagList, this.mobList, this.dimensionList, this.structureList, this.modList, this.containerList, this.exceptionList);
        }
    }
}
