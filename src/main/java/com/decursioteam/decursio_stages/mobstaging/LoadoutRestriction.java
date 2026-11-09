package com.decursioteam.decursio_stages.mobstaging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Optional;

public class LoadoutRestriction {

    private final ResourceLocation item;
    private String slot;
    private CompoundTag compoundTag;

    private final Integer chance;

    public LoadoutRestriction(ResourceLocation item, String slot, Optional<CompoundTag> compoundTag, Integer chance) {
        this.item = item;
        this.chance = chance;
        this.slot = slot;
        compoundTag.ifPresent(x -> this.compoundTag = compoundTag.get());
    }

    public static Codec<LoadoutRestriction> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").orElse(new ResourceLocation("")).forGetter(LoadoutRestriction::getResourceLocation),
                Codec.STRING.fieldOf("slot").orElse("").forGetter(LoadoutRestriction::getSlot),
                CompoundTag.CODEC.optionalFieldOf("nbt").orElse(null).forGetter(itemRestriction -> Optional.ofNullable(itemRestriction.compoundTag)),
                Codec.INT.fieldOf("chance").orElse(100).forGetter(LoadoutRestriction::getChance)
        ).apply(instance, LoadoutRestriction::new));
    }

    public Integer getChance() {
        return Math.min(100, chance);
    }

    @Nullable
    public CompoundTag getCompoundNBT() {
        return compoundTag;
    }

    public String getSlot() {
        return slot;
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
