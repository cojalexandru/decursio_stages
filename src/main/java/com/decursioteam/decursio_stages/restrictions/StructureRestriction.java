package com.decursioteam.decursio_stages.restrictions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public class StructureRestriction {

    private final Identifier structure;
    private final Boolean canUseBlock;
    private final Boolean canBreakBlock;

    private final Boolean canPlaceBlock;
    private final Optional<List<Identifier>> useBlockList;
    private final Optional<List<Identifier>> canPlaceBlockList;
    private final Optional<List<Identifier>> breakBlockList;

    public StructureRestriction(Identifier structure, Boolean canUseBlock, Boolean canPlaceBlock, Boolean canBreakBlock, Optional<List<Identifier>> useBlockList, Optional<List<Identifier>> canPlaceBlockList, Optional<List<Identifier>> breakBlockList) {
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
                Identifier.CODEC.fieldOf("structure").orElse(Identifier.parse("")).forGetter(StructureRestriction::getStructure),
                Codec.BOOL.fieldOf("can_use_block").orElse(false).forGetter(StructureRestriction::getCanUseBlock),
                Codec.BOOL.fieldOf("can_place_block").orElse(false).forGetter(StructureRestriction::getCanPlaceBlock),
                Codec.BOOL.fieldOf("can_break_block").orElse(false).forGetter(StructureRestriction::getCanBreakBlock),
                Identifier.CODEC.listOf().optionalFieldOf("can_use_block_list").forGetter(StructureRestriction::getUseBlockList),
                Identifier.CODEC.listOf().optionalFieldOf("can_place_block_list").forGetter(StructureRestriction::getCanPlaceBlockList),
                Identifier.CODEC.listOf().optionalFieldOf("can_break_block_list").forGetter(StructureRestriction::getBreakBlockList)
        ).apply(instance, StructureRestriction::new));
    }

    public Boolean getCanBreakBlock() {
        return canBreakBlock;
    }

    public Boolean getCanUseBlock() {
        return canUseBlock;
    }

    public Optional<List<Identifier>> getBreakBlockList() {
        return breakBlockList;
    }

    public Optional<List<Identifier>> getCanPlaceBlockList() {
        return canPlaceBlockList;
    }

    public Boolean getCanPlaceBlock() {
        return canPlaceBlock;
    }

    public Optional<List<Identifier>> getUseBlockList() {
        return useBlockList;
    }

    public Identifier getStructure() {
        return structure;
    }
}
