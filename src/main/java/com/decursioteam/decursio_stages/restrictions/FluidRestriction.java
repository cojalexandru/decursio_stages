package com.decursioteam.decursio_stages.restrictions;

import com.decursioteam.decursio_stages.DecursioStages;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Optional;

public class FluidRestriction {

    private ResourceLocation fluid;
    private String mod;
    private ResourceLocation tag;

    public FluidRestriction(Optional<ResourceLocation> fluid, Optional<ResourceLocation> tag, Optional<String> mod){
        try {
            fluid.ifPresent(x -> this.fluid = fluid.get());
            tag.ifPresent(x -> this.tag = tag.get());
            mod.ifPresent(x -> this.mod = mod.get());
        }
        catch (NullPointerException e)
        {
            DecursioStages.LOGGER.error("Error creating FluidRestriction: {}", e.getMessage());
        }

    }

    public static Codec<FluidRestriction> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("fluid").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.fluid)),
                ResourceLocation.CODEC.optionalFieldOf("tag").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.tag)),
                Codec.STRING.optionalFieldOf("mod").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.mod))
        ).apply(instance, FluidRestriction::new));
    }

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
        return fluid;
    }

    @Nullable
    public Fluid getFluid() {
        if (fluid == null) {
            return null;
        }
        return ForgeRegistries.FLUIDS.getValue(fluid);
    }
}
