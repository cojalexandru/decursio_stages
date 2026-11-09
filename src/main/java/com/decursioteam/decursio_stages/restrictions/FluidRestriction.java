package com.decursioteam.decursio_stages.restrictions;

import com.decursioteam.decursio_stages.DecursioStages;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.registries.BuiltInRegistries;

import org.jspecify.annotations.Nullable;
import java.util.Optional;

public class FluidRestriction {

    private Identifier fluid;
    private String mod;
    private Identifier tag;

    public FluidRestriction(Optional<Identifier> fluid, Optional<Identifier> tag, Optional<String> mod){
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
                Identifier.CODEC.optionalFieldOf("fluid").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.fluid)),
                Identifier.CODEC.optionalFieldOf("tag").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.tag)),
                Codec.STRING.optionalFieldOf("mod").orElse(null).forGetter(itemExclusion -> Optional.ofNullable(itemExclusion.mod))
        ).apply(instance, FluidRestriction::new));
    }

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
        return fluid;
    }

    @Nullable
    public Fluid getFluid() {
        if (fluid == null) {
            return null;
        }
        return BuiltInRegistries.FLUID.getValue(fluid);
    }
}
