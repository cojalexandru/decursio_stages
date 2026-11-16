package com.decursioteam.decursio_stages.restrictions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public class StructureRestriction {

    private final ResourceLocation structure;
    private final Boolean canUseBlock;
    private final Boolean canBreakBlock;

    private final Boolean canPlaceBlock;
    private final Optional<List<ResourceLocation>> useBlockList;
    private final Optional<List<ResourceLocation>> canPlaceBlockList;
    private final Optional<List<ResourceLocation>> breakBlockList;

    public StructureRestriction(ResourceLocation structure, Boolean canUseBlock, Boolean canPlaceBlock, Boolean canBreakBlock, Optional<List<ResourceLocation>> useBlockList, Optional<List<ResourceLocation>> canPlaceBlockList, Optional<List<ResourceLocation>> breakBlockList) {
        this.structure = structure;
        this.canUseBlock = canUseBlock;
        this.canPlaceBlock = canPlaceBlock;
        this.canBreakBlock = canBreakBlock;
        this.useBlockList = Optional.of(List.copyOf(useBlockList.orElse(List.of())));
        this.canPlaceBlockList = Optional.of(List.copyOf(canPlaceBlockList.orElse(List.of())));
        this.breakBlockList = Optional.of(List.copyOf(breakBlockList.orElse(List.of())));
    }

    public static Codec<StructureRestriction> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("structure").orElse(ResourceLocation.parse("")).forGetter(StructureRestriction::getStructure),
                Codec.BOOL.fieldOf("can_use_block").orElse(false).forGetter(StructureRestriction::getCanUseBlock),
                Codec.BOOL.fieldOf("can_place_block").orElse(false).forGetter(StructureRestriction::getCanPlaceBlock),
                Codec.BOOL.fieldOf("can_break_block").orElse(false).forGetter(StructureRestriction::getCanBreakBlock),
                ResourceLocation.CODEC.listOf().optionalFieldOf("can_use_block_list").forGetter(StructureRestriction::getUseBlockList),
                ResourceLocation.CODEC.listOf().optionalFieldOf("can_place_block_list").forGetter(StructureRestriction::getCanPlaceBlockList),
                ResourceLocation.CODEC.listOf().optionalFieldOf("can_break_block_list").forGetter(StructureRestriction::getBreakBlockList)
        ).apply(instance, StructureRestriction::new));
    }

    public Boolean getCanBreakBlock() {
        return canBreakBlock;
    }

    public Boolean getCanUseBlock() {
        return canUseBlock;
    }

    public Optional<List<ResourceLocation>> getBreakBlockList() {
        return breakBlockList;
    }

    public Optional<List<ResourceLocation>> getCanPlaceBlockList() {
        return canPlaceBlockList;
    }

    public Boolean getCanPlaceBlock() {
        return canPlaceBlock;
    }

    public Optional<List<ResourceLocation>> getUseBlockList() {
        return useBlockList;
    }

    public ResourceLocation getStructure() {
        return structure;
    }
}
