package com.decursioteam.decursio_stages.datagen;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.codec.RestrictionCodec;
import com.decursioteam.decursio_stages.codec.SettingsCodec;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class Restrictions {

    public static final Restrictions DEFAULT = new Restrictions(RestrictionCodec.DEFAULT, SettingsCodec.DEFAULT);

    public static Codec<Restrictions> codec(String name) {
        return RecordCodecBuilder.create(instance -> instance.group(
                RestrictionCodec.codec(name).fieldOf("Restriction Data").orElseGet((Consumer<String>) s -> DecursioStages.LOGGER.error("Restriction Data is required!"), null).forGetter(Restrictions::getData),
                SettingsCodec.CODEC.fieldOf("Settings").orElse(SettingsCodec.DEFAULT).forGetter(Restrictions::getSettingsCodec)
        ).apply(instance, Restrictions::new));
    }

    protected final RestrictionCodec restrictionCodec;
    protected final SettingsCodec settingsCodec;
    protected JsonObject rawData;

    private Restrictions(RestrictionCodec restrictionCodec, SettingsCodec settingsCodec) {
        this.restrictionCodec = restrictionCodec;
        this.settingsCodec = settingsCodec;
        this.rawData = RestrictionsData.getRegistry().getRawRestrictionData(restrictionCodec.getName());
    }

    public RestrictionCodec getData() {
        return restrictionCodec;
    }

    public SettingsCodec getSettingsCodec() {
        return settingsCodec;
    }

    @Nullable
    public JsonObject getRawData() {
        return rawData;
    }
}
