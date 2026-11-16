package com.decursioteam.decursio_stages.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 1.20.1 classic NBT wrapper matching the shared schema's "nbt" helper API. */
public final class ItemDataUtil {
    private ItemDataUtil() {}

    public static CompoundTag get(ItemStack stack) {
        return stack.getTag() != null ? stack.getTag() : new CompoundTag();
    }

    public static boolean has(ItemStack stack) {
        return stack.hasTag();
    }

    public static void apply(ItemStack stack, CompoundTag nbt) {
        if (nbt != null) stack.setTag(nbt.copy());
    }

    public static void applyPreview(ItemStack stack, CompoundTag nbt) {
        if (nbt != null) stack.setTag(nbt.copy());
    }
}
