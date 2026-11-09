package com.decursioteam.decursio_stages.restrictions;

import com.decursioteam.decursio_stages.utils.ItemDataUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import org.jspecify.annotations.Nullable;
import java.util.Optional;

public class ItemRestriction {

    private final Identifier item;
    private CompoundTag compoundTag;

    public ItemRestriction(Identifier item, Optional<CompoundTag> compoundTag) {
        this.item = item;
        compoundTag.ifPresent(x -> this.compoundTag = compoundTag.get());
    }

    public static Codec<ItemRestriction> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").orElse(Identifier.parse("")).forGetter(ItemRestriction::getResourceLocation),
                CompoundTag.CODEC.optionalFieldOf("nbt").orElse(null).forGetter(itemRestriction -> java.util.Optional.ofNullable(itemRestriction.compoundTag))
        ).apply(instance, ItemRestriction::new));
    }

    @Nullable
    public CompoundTag getCompoundNBT() {
        return compoundTag;
    }

    public Identifier getResourceLocation() {
        return item;
    }

    public Item getItem() {
        return BuiltInRegistries.ITEM.getValue(item);
    }

    public ItemStack getItemStack() {
        ItemStack itemStack = new ItemStack(getItem());
        if (getCompoundNBT() != null) ItemDataUtil.applyPreview(itemStack, getCompoundNBT());
        return itemStack;
    }
}
