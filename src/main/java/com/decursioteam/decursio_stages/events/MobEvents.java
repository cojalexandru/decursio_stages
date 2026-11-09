package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.mobstaging.EffectsCodec;
import com.decursioteam.decursio_stages.mobstaging.MobRestriction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.decursioteam.decursio_stages.utils.ResourceUtil.getApplyToFakePlayer;
import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;

public class MobEvents {

    private final Random random = new Random();
    @SubscribeEvent
    public void onEntitySpawn(MobSpawnEvent.FinalizeSpawn event) {

        var ref = new Object() {
            Player closestPlayer = null;
        };

        // Find the closest player
        for (ServerPlayer player : Objects.requireNonNull(event.getLevel().getServer()).getPlayerList().getPlayers()) {
            if (ref.closestPlayer == null || player.distanceTo(event.getEntity()) < ref.closestPlayer.distanceTo(event.getEntity())) {
                ref.closestPlayer = player;
            }
        }

        if (ref.closestPlayer == null) {
            return; // No players, exit early
        }

        ResourceLocation entityID = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        String entityCategory = event.getEntity().getType().getCategory().getName();
        AtomicBoolean shouldSpawn = new AtomicBoolean(true); // Tracks whether to cancel the spawn

        // Iterate through restrictions
        Registry.getRestrictions().forEach(restriction -> {
            if(!getApplyToFakePlayer(restriction) && ref.closestPlayer instanceof FakePlayer) return;

            String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage().toLowerCase(Locale.ROOT);
            List<MobRestriction> mobList = RestrictionsData.getRestrictionData(restriction).getData().getMobList();

            // Check if player has the required stage
            if(hasStage(ref.closestPlayer, stage)) {

                for (MobRestriction e : mobList) {
                    boolean matchesEntityID = e.getEntityID().isPresent() && e.getEntityID().get().equals(entityID);
                    boolean matchesModID = e.getEntityModID().isPresent() && e.getEntityModID().get().equals(entityID.getNamespace());
                    boolean matchesMobCategory = e.getMobCategory().isPresent() && e.getMobCategory().get().equals(entityCategory);
                    boolean matchesEntityTag = e.getEntityTag().isPresent() && ForgeRegistries.ENTITY_TYPES.tags().getReverseTag(event.getEntity().getType()).isPresent() &&
                            ForgeRegistries.ENTITY_TYPES.tags().getReverseTag(event.getEntity().getType()).get().containsTag(ForgeRegistries.ENTITY_TYPES.tags().createTagKey(e.getEntityTag().get()));

                    // Check entity ID, category, or tag
                    if (matchesEntityID || matchesMobCategory || matchesEntityTag || matchesModID) {

                        if(e.getListType().equals("WHITELIST")) {

                            // Check spawn type
                            if (!e.getSpawnType().contains(event.getSpawnType())) {
                                shouldSpawn.set(false);
                            }
                            int lightLevel = event.getLevel().getLightEmission(event.getEntity().blockPosition());

                            // Light level restrictions
                            if (e.getMaxLight().isPresent() && e.getMaxLight().get() <= lightLevel) {
                                shouldSpawn.set(false);
                            }
                            if (e.getMinLight().isPresent() && e.getMinLight().get() >= lightLevel) {
                                shouldSpawn.set(false);
                            }
                            // Biome checks
                            if (e.getBiomes().isPresent()) {
                                boolean biomeMatch = e.getBiomes().get().stream()
                                        .anyMatch(resourceLocation -> event.getEntity().level().getBiome(event.getEntity().blockPosition()).is(resourceLocation));
                                if (!biomeMatch) {
                                    shouldSpawn.set(false);
                                }
                            }
                            // Health setting
                            e.getHealth().ifPresent(health -> {
                                float prevMaxHealth = event.getEntity().getMaxHealth();
                                Objects.requireNonNull(event.getEntity().getAttribute(Attributes.MAX_HEALTH)).addPermanentModifier(
                                        new AttributeModifier(
                                        "Max Health Modifier",  // Name
                                        health - prevMaxHealth,           // Amount (subtracting default max health)
                                        AttributeModifier.Operation.ADDITION  // Operation type
                                ));
                                event.getEntity().setHealth(health);
                            });
                            // Effects application
                            if (e.getEffects().isPresent()) {
                                event.setCanceled(true);
                                for (EffectsCodec effect : e.getEffects().get()) {
                                    if (random.nextInt(100) < effect.getChance()) {
                                        ResourceLocation effectLocation = effect.getResourceLocation();
                                        int duration = effect.getDuration();
                                        if (effect.getDuration() == 0) duration = Integer.MAX_VALUE;
                                        int amplifier = effect.getAmplifier();

                                        MobEffect mobEffect = ForgeRegistries.MOB_EFFECTS.getValue(effectLocation);
                                        if (mobEffect != null) {
                                            event.getEntity().addEffect(new MobEffectInstance(mobEffect, duration, amplifier));
                                        }
                                    }
                                }
                            }
                            // Loadout application
                            if (e.getLoadoutList().isPresent()) {
                                event.setCanceled(true);
                                e.getLoadoutList().get().forEach(item -> {
                                    if (random.nextInt(100) < item.getChance()) {
                                        switch (item.getSlot().toUpperCase(Locale.ROOT)) {
                                            case "MAINHAND":
                                                event.getEntity().setItemInHand(InteractionHand.MAIN_HAND, item.getItemStack());
                                                break;
                                            case "OFFHAND":
                                                event.getEntity().setItemInHand(InteractionHand.OFF_HAND, item.getItemStack());
                                                break;
                                            case "FEET":
                                                event.getEntity().setItemSlot(EquipmentSlot.FEET, item.getItemStack());
                                                break;
                                            case "LEGS":
                                                event.getEntity().setItemSlot(EquipmentSlot.LEGS, item.getItemStack());
                                                break;
                                            case "CHEST":
                                                event.getEntity().setItemSlot(EquipmentSlot.CHEST, item.getItemStack());
                                                break;
                                            case "HEAD":
                                                event.getEntity().setItemSlot(EquipmentSlot.HEAD, item.getItemStack());
                                                break;
                                        }
                                    }
                                });
                            }
                        }
                        if(e.getListType().equals("BLACKLIST")){

                            // Check spawn type
                            if (e.getSpawnType().contains(event.getSpawnType())) {
                                shouldSpawn.set(false);
                            }

                            int lightLevel = event.getLevel().getLightEmission(event.getEntity().blockPosition());

                            // Light level restrictions
                            if (e.getMaxLight().isPresent() && e.getMaxLight().get() <= lightLevel) {
                                shouldSpawn.set(false);
                            }
                            if (e.getMinLight().isPresent() && e.getMinLight().get() >= lightLevel) {
                                shouldSpawn.set(false);
                            }

                            // Biome checks
                            if (e.getBiomes().isPresent()) {
                                boolean biomeMatch = e.getBiomes().get().stream()
                                        .anyMatch(resourceLocation -> event.getEntity().level().getBiome(event.getEntity().blockPosition()).is(resourceLocation));
                                if (!biomeMatch) {
                                    shouldSpawn.set(true);
                                }
                            }

                            // Health setting
                            if(e.getHealth().isPresent() && event.getEntity().getMaxHealth() == e.getHealth().get().floatValue()) {
                                shouldSpawn.set(false);
                            }

                            // Effects application
                            if (e.getEffects().isPresent()) {
                                for (EffectsCodec effect : e.getEffects().get()) {
                                    ResourceLocation effectLocation = effect.getResourceLocation();
                                    int duration = (effect.getDuration() == 0) ? Integer.MAX_VALUE : effect.getDuration();
                                    int amplifier = effect.getAmplifier();

                                    MobEffect mobEffect = ForgeRegistries.MOB_EFFECTS.getValue(effectLocation);
                                    if (mobEffect != null) {
                                        if(event.getEntity().getActiveEffects().stream().anyMatch(effectz -> effectz.equals(new MobEffectInstance(mobEffect, duration, amplifier)))){
                                            shouldSpawn.set(false);
                                        }
                                    }
                                }
                            }

                            // Loadout application
                            if (e.getLoadoutList().isPresent()) {
                                e.getLoadoutList().get().forEach(item -> {
                                    if (random.nextInt(100) < item.getChance()) {
                                        switch (item.getSlot().toUpperCase(Locale.ROOT)) {
                                            case "MAINHAND":
                                                if(!event.getEntity().getItemBySlot(EquipmentSlot.MAINHAND).is(item.getItem()))
                                                {
                                                    shouldSpawn.set(false);
                                                }
                                                break;
                                            case "OFFHAND":
                                                if(!event.getEntity().getItemBySlot(EquipmentSlot.OFFHAND).is(item.getItem()))
                                                {
                                                    shouldSpawn.set(false);
                                                }
                                                break;
                                            case "FEET":
                                                if(!event.getEntity().getItemBySlot(EquipmentSlot.FEET).is(item.getItem()))
                                                {
                                                    shouldSpawn.set(false);
                                                }
                                                break;
                                            case "LEGS":
                                                if(!event.getEntity().getItemBySlot(EquipmentSlot.LEGS).is(item.getItem()))
                                                {
                                                    shouldSpawn.set(false);
                                                }
                                                break;
                                            case "CHEST":
                                                if(!event.getEntity().getItemBySlot(EquipmentSlot.CHEST).is(item.getItem()))
                                                {
                                                    shouldSpawn.set(false);
                                                }
                                                break;
                                            case "HEAD":
                                                if(!event.getEntity().getItemBySlot(EquipmentSlot.HEAD).is(item.getItem()))
                                                {
                                                    shouldSpawn.set(false);
                                                }
                                                break;
                                        }
                                    }
                                });
                            }
                        }
                    } else {
                        // No match found, spawn
                    }
                }
            }
        });

        // Cancel spawn if necessary
        if (!shouldSpawn.get()) {
            cancelSpawn(event);
        }
    }

    private void cancelSpawn(MobSpawnEvent.FinalizeSpawn event) {
        event.setSpawnCancelled(true);
        event.setResult(Event.Result.DENY);
        event.setCanceled(true);
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onDebugOverlay(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug) {
            LocalPlayer player = mc.player;
            if (player != null) {
                HitResult hitResult = mc.hitResult;
                if (hitResult == null) {
                    hitResult = player.pick(20.0D, 0.0F, false);
                }

                if (hitResult.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHitResult = (EntityHitResult) hitResult;
                    Entity target = entityHitResult.getEntity();

                    if (target instanceof LivingEntity livingTarget) {
                        event.getRight().add("Category: " + livingTarget.getType().getCategory().getName());
                    }
                }
            }
        }
    }
}
