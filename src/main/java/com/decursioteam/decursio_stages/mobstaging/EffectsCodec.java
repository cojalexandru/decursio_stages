package com.decursioteam.decursio_stages.mobstaging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public class EffectsCodec {

    private final ResourceLocation effect;
    private final Integer amplifier;
    private final Integer duration;

    private final Integer chance;

    public EffectsCodec(ResourceLocation effect, Integer amplifier, Integer duration, Integer chance) {
        this.effect = effect;
        this.amplifier = amplifier;
        this.duration = duration;
        this.chance = chance;
    }

    public static Codec<EffectsCodec> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("effect").orElse(new ResourceLocation("")).forGetter(EffectsCodec::getResourceLocation),
                Codec.INT.fieldOf("amplifier").orElse(1).forGetter(EffectsCodec::getAmplifier),
                Codec.INT.fieldOf("duration").orElse(100).forGetter(EffectsCodec::getDuration),
                Codec.INT.fieldOf("chance").orElse(100).forGetter(EffectsCodec::getChance)
        ).apply(instance, EffectsCodec::new));
    }

    public Integer getChance() {
        return Math.min(100, chance);
    }

    public Integer getDuration() {
        return duration;
    }

    public Integer getAmplifier() {
        return amplifier;
    }

    public ResourceLocation getResourceLocation() {
        return effect;
    }
}
