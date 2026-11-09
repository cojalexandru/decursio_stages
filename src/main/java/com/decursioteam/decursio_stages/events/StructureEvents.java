package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.decursioteam.decursio_stages.utils.ResourceUtil.getApplyToFakePlayer;
import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;

public class StructureEvents {
    private final Set<UUID> playersInRestrictedStructures = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)) return;

        // Aggregate before changing state: an unrelated structure must not undo a match.
        boolean inside = isInsideAnyRestrictedStructure(player);
        boolean wasInside = playersInRestrictedStructures.contains(player.getUUID());
        if (inside == wasInside) return;

        if (inside) playersInRestrictedStructures.add(player.getUUID());
        else playersInRestrictedStructures.remove(player.getUUID());
        // Vanilla action-bar messages expire naturally; only actual transitions reset the timer.
        player.sendOverlayMessage(Component.translatable(inside
                ? "decursio_stages.structure.enter" : "decursio_stages.structure.leave"));
    }

    private boolean isInsideAnyRestrictedStructure(ServerPlayer player) {
        for (String name : Registry.getRestrictions()) {
            if (player instanceof FakePlayer && !getApplyToFakePlayer(name)) continue;
            var restriction = RestrictionsData.getRestrictionData(name).getData();
            if (restriction.getStructureList().isEmpty()
                    || !hasStage(player, restriction.getStage().toLowerCase(Locale.ROOT))) continue;
            for (var entry : restriction.getStructureList()) {
                if (isInsideStructure(player, entry.getStructure())) return true;
            }
        }
        return false;
    }

    protected boolean isInsideStructure(ServerPlayer player, Identifier id) {
        var manager = player.level().structureManager();
        var structure = manager.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id);
        return structure != null && manager.getStructureAt(player.getOnPos(), structure).isValid();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        playersInRestrictedStructures.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        playersInRestrictedStructures.clear();
    }
}
