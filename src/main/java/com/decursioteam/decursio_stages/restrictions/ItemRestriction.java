package com.decursioteam.decursio_stages.restrictions;

import com.decursioteam.decursio_stages.utils.ItemDataUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

public class ItemRestriction {

    private final ResourceLocation item;
    private CompoundTag compoundTag;

    public ItemRestriction(ResourceLocation item, Optional<CompoundTag> compoundTag) {
        this.item = item;
        compoundTag.ifPresent(x -> this.compoundTag = compoundTag.get());
    }

    public static Codec<ItemRestriction> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").orElse(new ResourceLocation("")).forGetter(ItemRestriction::getResourceLocation),
                CompoundTag.CODEC.optionalFieldOf("nbt").orElse(null).forGetter(itemRestriction -> Optional.ofNullable(itemRestriction.compoundTag))
        ).apply(instance, ItemRestriction::new));
    }

    @Nullable
    public CompoundTag getCompoundNBT() {
        return compoundTag;
    }

    public ResourceLocation getResourceLocation() {
        return item;
    }

    public Item getItem() {
        return BuiltInRegistries.ITEM.get(item);
    }

    public ItemStack getItemStack() {
        ItemStack itemStack = new ItemStack(getItem());
        if (getCompoundNBT() != null) ItemDataUtil.applyPreview(itemStack, getCompoundNBT());
        return itemStack;
    }
}
