package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.codec.RestrictionCodec;
import com.decursioteam.decursio_stages.restrictions.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Immutable indices for one parsed rule. Stack contents and tag membership remain live. */
public final class CompiledRestriction {
    public static final CompiledRestriction EMPTY = new CompiledRestriction(RestrictionCodec.DEFAULT);
    public final Set<ItemRestriction> items;
    public final Set<ItemExclusion> exceptions;
    public final Set<ResourceLocation> tags;
    public final Set<String> mods, containers;
    public final Set<FluidRestriction> fluids;
    public final Set<DimensionRestriction> dimensions;
    private final Map<ResourceLocation, List<ItemRestriction>> itemsById;
    private final Map<ResourceLocation, List<ItemExclusion>> exceptionsById;
    private final Set<String> exceptionMods;
    private final List<TagKey<Item>> itemTags, exceptionTags;

    public CompiledRestriction(RestrictionCodec data) {
        items = Set.copyOf(data.getItemList()); exceptions = Set.copyOf(data.getExceptionList());
        tags = Set.copyOf(data.getTagList()); mods = Set.copyOf(data.getModList());
        fluids = Set.copyOf(data.getFluidList()); dimensions = Set.copyOf(data.getDimensionList());
        containers = Set.copyOf(data.getContainerList());
        Map<ResourceLocation, List<ItemRestriction>> indexedItems = new HashMap<>();
        for (var rule : items) indexedItems.computeIfAbsent(rule.getResourceLocation(), id -> new ArrayList<>()).add(rule);
        itemsById = freeze(indexedItems);
        Map<ResourceLocation, List<ItemExclusion>> indexedExceptions = new HashMap<>();
        Set<String> excludedMods = new HashSet<>();
        List<TagKey<Item>> excludedTags = new ArrayList<>();
        for (var rule : exceptions) {
            // Preserve the original selector precedence for entries containing multiple selectors.
            if (rule.getResourceLocation() != null) indexedExceptions.computeIfAbsent(rule.getResourceLocation(), id -> new ArrayList<>()).add(rule);
            else if (rule.getMod() != null) excludedMods.add(rule.getMod());
            else if (rule.getTag() != null) excludedTags.add(TagKey.create(Registries.ITEM, rule.getTag()));
        }
        exceptionsById = freeze(indexedExceptions);
        exceptionMods = Set.copyOf(excludedMods);
        exceptionTags = List.copyOf(excludedTags);
        itemTags = tags.stream().map(id -> TagKey.create(Registries.ITEM, id)).toList();
    }

    private static <T> Map<ResourceLocation, List<T>> freeze(Map<ResourceLocation, List<T>> map) {
        map.replaceAll((id, values) -> List.copyOf(values));
        return Map.copyOf(map);
    }

    public boolean excepted(ItemStack stack) {
        ResourceLocation id = ResourceUtil.getRegistryName(stack.getItem());
        if (exceptionMods.contains(id.getNamespace())) return true;
        for (var rule : exceptionsById.getOrDefault(id, List.of())) {
            if (rule.getCompoundNBT() == null || NBTComparisonUtil.areNBTCompoundsEqual(rule.getCompoundNBT(), ItemDataUtil.get(stack))) return true;
        }
        for (var tag : exceptionTags) if (stack.is(tag)) return true;
        return false;
    }

    public boolean matches(ItemStack stack, ResourceUtil.CHECK_TYPES type) {
        if (stack.isEmpty() || excepted(stack)) return false;
        ResourceLocation id = ResourceUtil.getRegistryName(stack.getItem());
        if (type == ResourceUtil.CHECK_TYPES.ITEM || type == ResourceUtil.CHECK_TYPES.ALL) {
            for (var rule : itemsById.getOrDefault(id, List.of()))
                if (NBTComparisonUtil.doesItemMatchRestriction(rule, stack)) return true;
        }
        if ((type == ResourceUtil.CHECK_TYPES.MOD || type == ResourceUtil.CHECK_TYPES.ALL) && mods.contains(id.getNamespace())) return true;
        if (type == ResourceUtil.CHECK_TYPES.TAG || type == ResourceUtil.CHECK_TYPES.ALL)
            for (var tag : itemTags) if (stack.is(tag)) return true;
        return false;
    }
}
