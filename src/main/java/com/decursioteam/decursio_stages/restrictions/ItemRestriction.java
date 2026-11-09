package com.decursioteam.decursio_stages.restrictions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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
                CompoundTag.CODEC.optionalFieldOf("nbt").orElse(null).forGetter(itemRestriction -> java.util.Optional.ofNullable(itemRestriction.compoundTag))
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
        return ForgeRegistries.ITEMS.getValue(item);
    }

    public ItemStack getItemStack() {
        ItemStack itemStack = new ItemStack(getItem());
        if (getCompoundNBT() != null) itemStack.setTag(getCompoundNBT());
        return itemStack;
    }
}
