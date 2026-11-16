package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.mobstaging.MobRestriction;
import com.decursioteam.decursio_stages.utils.StageUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class MobEvents {
    private final Map<Mob, List<MobRestriction>> pending = Collections.synchronizedMap(new WeakHashMap<>());

    @SubscribeEvent
    public void onEntitySpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        pending.remove(mob);
        var level = event.getLevel();
        ServerPlayer player = nearestPlayer(level.getLevel(), mob);
        if (player == null || event.isSpawnCancelled()) return;
        int light = level.getMaxLocalRawBrightness(mob.blockPosition());
        List<MobRestriction> modifiers = new ArrayList<>();
        var registry = RestrictionsData.getRegistry();
        for (String name : registry.getRestrictions().keySet().stream().sorted().toList()) {
            var restriction = registry.getRestrictions().get(name);
            if (player instanceof FakePlayer && !restriction.getSettingsCodec().getApplyToFakePlayers()) continue;
            if (!StageUtil.hasStage(player, restriction.getData().getStage().toLowerCase(Locale.ROOT))) continue;
            for (var rule : restriction.getData().getMobList()) {
                if (!selects(rule, mob)) continue;
                boolean context = rule.getSpawnType().contains(event.getSpawnType())
                        && rule.getMinLight().map(min -> light >= min).orElse(true)
                        && rule.getMaxLight().map(max -> light <= max).orElse(true)
                        && rule.getBiomes().map(ids -> ids.stream().anyMatch(id -> level.getBiome(mob.blockPosition()).is(id))).orElse(true);
                boolean whitelist = rule.getListType().equals("WHITELIST");
                if (whitelist ? !context : context) {
                    event.setSpawnCancelled(true);
                    event.setResult(Event.Result.DENY);
                    event.setCanceled(true);
                    return; // No other rule can undo a denial, and no denied mob receives modifiers.
                }
                if (whitelist) modifiers.add(rule);
            }
        }
        if (!modifiers.isEmpty()) pending.put(mob, List.copyOf(modifiers));
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, Mob mob) {
        ServerPlayer closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double current = player.distanceToSqr(mob);
            if (current < distance || current == distance && closest != null && player.getUUID().compareTo(closest.getUUID()) < 0) {
                closest = player; distance = current;
            }
        }
        return closest;
    }

    private static boolean selects(MobRestriction rule, Mob mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return rule.getEntityID().map(id::equals).orElse(false)
                || rule.getEntityModID().map(id.getNamespace()::equals).orElse(false)
                || rule.getMobCategory().map(mob.getType().getCategory().getName()::equals).orElse(false)
                || rule.getEntityTag().map(tag -> mob.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag))).orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterInitialization(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide) return;
        List<MobRestriction> rules = pending.remove(mob);
        if (rules == null || mob.isSpawnCancelled()) return;
        // Join occurs after finalizeSpawn; don't suppress vanilla initialization to keep custom equipment.
        for (var rule : rules) {
            rule.getHealth().ifPresent(health -> {
                var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
                if (attribute != null) { attribute.setBaseValue(health); mob.setHealth(mob.getMaxHealth()); }
            });
            rule.getEffects().ifPresent(effects -> effects.forEach(effect -> {
                if (mob.getRandom().nextInt(100) >= effect.getChance()) return;
                MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(effect.getResourceLocation());
                if (mobEffect != null) {
                    int duration = effect.getDuration() == 0 ? -1 : effect.getDuration();
                    mob.addEffect(new MobEffectInstance(mobEffect, duration, effect.getAmplifier()));
                }
            }));
            rule.getLoadoutList().ifPresent(items -> items.forEach(item -> {
                if (mob.getRandom().nextInt(100) >= item.getChance() || !BuiltInRegistries.ITEM.containsKey(item.getResourceLocation())) return;
                mob.setItemSlot(EquipmentSlot.valueOf(item.getSlot().toUpperCase(Locale.ROOT)), item.getItemStack());
            }));
        }
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) { pending.clear(); }
}
