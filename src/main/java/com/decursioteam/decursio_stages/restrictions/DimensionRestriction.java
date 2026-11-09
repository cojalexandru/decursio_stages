package com.decursioteam.decursio_stages.restrictions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public class DimensionRestriction {
    private final String message;
    private final ResourceLocation dimension;

    private DimensionRestriction(String message, ResourceLocation dimension){
        this.message = message;
        this.dimension = dimension;
    }

    public static Codec<DimensionRestriction> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("message").orElse("You don't have access to this dimension").forGetter(DimensionRestriction::getMessage),
                ResourceLocation.CODEC.fieldOf("dimension").orElse(new ResourceLocation("")).forGetter(DimensionRestriction::getDimension)
        ).apply(instance, DimensionRestriction::new));
    }

    public String getMessage() {
        return message;
    }

    public ResourceLocation getDimension() {
        return dimension;
    }
}
