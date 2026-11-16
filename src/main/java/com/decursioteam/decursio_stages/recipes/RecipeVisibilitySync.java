package com.decursioteam.decursio_stages.recipes;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.network.messages.SyncRecipeVisibilityMessage;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

@EventBusSubscriber(modid = "decursio_stages")
public final class RecipeVisibilitySync {
    private record State(Set<Identifier> blocked, Object displays) {}
    private static final Map<UUID, State> STATES = new HashMap<>();
    public static ClientboundRecipeBookAddPacket filter(ServerPlayer player, ClientboundRecipeBookAddPacket packet) {
        var blocked = RecipeStages.policy(player);
        var manager = player.level().getServer().getRecipeManager();
        var entries = packet.entries().stream().filter(entry -> {
            var info = manager.getRecipeFromDisplay(entry.contents().id());
            return info == null || !blocked.contains(info.parent().id().identifier());
        }).toList();
        return entries.size() == packet.entries().size() ? packet : new ClientboundRecipeBookAddPacket(entries, packet.replace());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var manager = player.level().getServer().getRecipeManager();
        // Display IDs are rebuilt on resource reload, even when blocked recipe IDs stay identical.
        State current = new State(RecipeStages.policy(player), manager.recipeMap());
        if (current.equals(STATES.put(player.getUUID(), current))) return;
        Set<Integer> displays = new HashSet<>();
        for (var id : current.blocked) manager.listDisplaysForRecipe(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id), display -> displays.add(display.id().index()));
        DecursioStages.NETWORK.sendToPlayer(player, new SyncRecipeVisibilityMessage(current.blocked, displays));
        player.getRecipeBook().sendInitialRecipeBook(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { STATES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); }
    private RecipeVisibilitySync() {}
}
