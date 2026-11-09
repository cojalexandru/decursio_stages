package com.decursioteam.decursio_stages.restrictions;

import com.decursioteam.decursio_stages.utils.ItemDataUtil;
import com.decursioteam.decursio_stages.DecursioStages;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.Optional;

public class ItemExclusion {

    private ResourceLocation item;
    private CompoundTag compoundTag;
    private String mod;
    private ResourceLocation tag;
    private ResourceLocation fluid;
    private ResourceLocation fluidTag;
    private String fluidMod;

    public ItemExclusion(Optional<ResourceLocation> item, Optional<CompoundTag> compoundTag, Optional<ResourceLocation> tag, Optional<String> mod){
        this(item, compoundTag, tag, mod, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ItemExclusion(Optional<ResourceLocation> item, Optional<CompoundTag> compoundTag, Optional<ResourceLocation> tag, Optional<String> mod,
                         Optional<ResourceLocation> fluid, Optional<ResourceLocation> fluidTag, Optional<String> fluidMod){
        fluidMod.ifPresent(x -> this.fluidMod = x);
        fluid.ifPresent(x -> this.fluid = x);
        fluidTag.ifPresent(x -> this.fluidTag = x);
        try {
            item.ifPresent(x -> this.item = item.get());
            tag.ifPresent(x -> this.tag = tag.get());
            mod.ifPresent(x -> this.mod = mod.get());
            compoundTag.ifPresent(x -> this.compoundTag = compoundTag.get());
        }
        catch (NullPointerException e)
        {
            DecursioStages.LOGGER.error("Error creating FluidRestriction: {}", e.getMessage());
        }

    }

    public static Codec<ItemExclusion> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("item").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.item)),
                CompoundTag.CODEC.optionalFieldOf("nbt").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.compoundTag)),
                ResourceLocation.CODEC.optionalFieldOf("tag").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.tag)),
                Codec.STRING.optionalFieldOf("mod").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.mod)),
                ResourceLocation.CODEC.optionalFieldOf("fluid").forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.fluid)),
                ResourceLocation.CODEC.optionalFieldOf("fluidTag").forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.fluidTag)),
                Codec.STRING.optionalFieldOf("fluidMod").forGetter(e -> Optional.ofNullable(e.fluidMod))
        ).apply(instance, ItemExclusion::new));
    }

    @Nullable
    public CompoundTag getCompoundNBT() {
        return compoundTag;
    }

    /** Fluid exception: keeps this fluid usable/visible even if its mod or a fluid tag is restricted. */
    @Nullable
    public ResourceLocation getFluid() {
        return fluid;
    }

    @Nullable
    public ResourceLocation getFluidTag() {
        return fluidTag;
    }

    @Nullable
    public String getFluidMod() { return fluidMod; }

    @Nullable
    public String getMod() {
        return mod;
    }

    @Nullable
    public ResourceLocation getTag() {
        return tag;
    }

    @Nullable
    public ResourceLocation getResourceLocation() {
        return item;
    }

    @Nullable
    public Item getItem() {
        return item == null ? null : BuiltInRegistries.ITEM.get(item);
    }

    @Nullable
    public ItemStack getItemStack() {
        ItemStack itemStack = new ItemStack(getItem());
        if(getCompoundNBT() != null) ItemDataUtil.applyPreview(itemStack, getCompoundNBT());
        return itemStack;
    }
}
