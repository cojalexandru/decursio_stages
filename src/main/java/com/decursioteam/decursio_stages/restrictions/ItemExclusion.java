package com.decursioteam.decursio_stages.restrictions;

import com.decursioteam.decursio_stages.utils.ItemDataUtil;
import com.decursioteam.decursio_stages.DecursioStages;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import org.jspecify.annotations.Nullable;
import java.util.Optional;

public class ItemExclusion {

    private Identifier item;
    private CompoundTag compoundTag;
    private String mod;
    private Identifier tag;
    private Identifier fluid;
    private Identifier fluidTag;
    private String fluidMod;

    public ItemExclusion(Optional<Identifier> item, Optional<CompoundTag> compoundTag, Optional<Identifier> tag, Optional<String> mod){
        this(item, compoundTag, tag, mod, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ItemExclusion(Optional<Identifier> item, Optional<CompoundTag> compoundTag, Optional<Identifier> tag, Optional<String> mod,
                         Optional<Identifier> fluid, Optional<Identifier> fluidTag, Optional<String> fluidMod){
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
                Identifier.CODEC.optionalFieldOf("item").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.item)),
                CompoundTag.CODEC.optionalFieldOf("nbt").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.compoundTag)),
                Identifier.CODEC.optionalFieldOf("tag").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.tag)),
                Codec.STRING.optionalFieldOf("mod").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.mod)),
                Identifier.CODEC.optionalFieldOf("fluid").forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.fluid)),
                Identifier.CODEC.optionalFieldOf("fluidTag").forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.fluidTag)),
                Codec.STRING.optionalFieldOf("fluidMod").forGetter(e -> Optional.ofNullable(e.fluidMod))
        ).apply(instance, ItemExclusion::new));
    }

    @Nullable
    public CompoundTag getCompoundNBT() {
        return compoundTag;
    }

    /** Fluid exception: keeps this fluid usable/visible even if its mod or a fluid tag is restricted. */
    @Nullable
    public Identifier getFluid() {
        return fluid;
    }

    @Nullable
    public Identifier getFluidTag() {
        return fluidTag;
    }

    @Nullable
    public String getFluidMod() { return fluidMod; }

    @Nullable
    public String getMod() {
        return mod;
    }

    @Nullable
    public Identifier getTag() {
        return tag;
    }

    @Nullable
    public Identifier getResourceLocation() {
        return item;
    }

    @Nullable
    public Item getItem() {
        return item == null ? null : BuiltInRegistries.ITEM.getValue(item);
    }

    @Nullable
    public ItemStack getItemStack() {
        ItemStack itemStack = new ItemStack(getItem());
        if(getCompoundNBT() != null) ItemDataUtil.applyPreview(itemStack, getCompoundNBT());
        return itemStack;
    }
}
