package com.decursioteam.decursio_stages.mobstaging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MobRestriction {

    private final Optional<ResourceLocation> entityID;
    private final Optional<String> entityModID;
    private final Optional<String> mobCategory;
    private final Optional<ResourceLocation> entityTag;
    private final Optional<Integer> minLight;
    private final Optional<Integer> maxLight;
    private final Optional<List<ResourceLocation>> biomes;
    private final String listType;
    private final List<String> spawnType;
    private final Optional<Integer> health;
    private final Optional<List<EffectsCodec>> effects;
    private final Optional<List<LoadoutRestriction>> loadoutList;

    public MobRestriction(List<String> spawnType, String listType, Optional<ResourceLocation> entityID, Optional<String> entityModID, Optional<String> mobCategory, Optional<ResourceLocation> entityTag, Optional<Integer> minLight, Optional<Integer> maxLight, Optional<List<ResourceLocation>> biomes, Optional<Integer> health, Optional<List<EffectsCodec>> effects, Optional<List<LoadoutRestriction>> loadoutList) {
        this.spawnType = spawnType;
        this.listType = listType;
        this.entityModID = entityModID;
        this.entityID = entityID;
        this.mobCategory = mobCategory;
        this.entityTag = entityTag;
        this.minLight = minLight;
        this.maxLight = maxLight;
        this.biomes = biomes;
        this.health = health;
        this.effects = effects;
        this.loadoutList = loadoutList;
    }

    public static Codec<MobRestriction> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().fieldOf("spawnType").forGetter(MobRestriction::getSpawnTypeString),
                Codec.STRING.fieldOf("whitelist_blacklist").forGetter(MobRestriction::getListType),
                ResourceLocation.CODEC.optionalFieldOf("entityID").forGetter(MobRestriction::getEntityID),
                Codec.STRING.optionalFieldOf("entityModID").forGetter(MobRestriction::getEntityModID),
                Codec.STRING.optionalFieldOf("mobCategory").forGetter(MobRestriction::getMobCategory),
                ResourceLocation.CODEC.optionalFieldOf("entityTag").forGetter(MobRestriction::getEntityTag),
                Codec.INT.optionalFieldOf("minLight").forGetter(MobRestriction::getMinLight),
                Codec.INT.optionalFieldOf("maxLight").forGetter(MobRestriction::getMaxLight),
                ResourceLocation.CODEC.listOf().optionalFieldOf("biomes").forGetter(MobRestriction::getBiomes),
                Codec.INT.optionalFieldOf("health").forGetter(MobRestriction::getHealth),
                EffectsCodec.codec().listOf().optionalFieldOf("effects").forGetter(MobRestriction::getEffects),
                LoadoutRestriction.codec().listOf().optionalFieldOf("loadout").orElse(null).forGetter(MobRestriction::getLoadoutList)
        ).apply(instance, (spawnType1, listType1, entityID1, entityModID1, mobCategory1, entityTag1, minlight1, maxlight1, biomes1, health1, effects1, loadoutList1) -> new MobRestriction(spawnType1, listType1, entityID1, entityModID1, mobCategory1, entityTag1, minlight1, maxlight1, biomes1, health1, effects1, loadoutList1)));
    }

    public Optional<Integer> getHealth() {
        return health;
    }

    public List<String> getSpawnTypeString() {
        return spawnType;
    }

    public List<MobSpawnType> getSpawnType() {
        List<MobSpawnType> list = new ArrayList<>();
        for (String s : spawnType) {
            list.add(MobSpawnType.valueOf(s));
        }
        return list;
    }

    public String getListType() {
        return listType;
    }

    public Optional<String> getEntityModID() {
        return entityModID;
    }

    public Optional<Integer> getMaxLight() {
        return maxLight;
    }

    public Optional<Integer> getMinLight() {
        return minLight;
    }

    public Optional<List<EffectsCodec>> getEffects() {
        return effects;
    }

    public Optional<List<ResourceLocation>> getBiomes() {
        return biomes;
    }

    public Optional<ResourceLocation> getEntityTag() {
        return entityTag;
    }

    public Optional<ResourceLocation> getEntityID() {
        return entityID;
    }

    public Optional<String> getMobCategory() {
        return mobCategory;
    }

    public Optional<List<LoadoutRestriction>> getLoadoutList() {
        return loadoutList;
    }
}
