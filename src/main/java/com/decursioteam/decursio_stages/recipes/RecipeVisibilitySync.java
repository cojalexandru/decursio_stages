package com.decursioteam.decursio_stages.recipes;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.network.messages.SyncRecipeVisibilityMessage;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = "decursio_stages")
public final class RecipeVisibilitySync {
    private record State(Set<ResourceLocation> blocked, Object recipes) {}
    private static final Map<UUID, State> STATES = new HashMap<>();

    public static ClientboundRecipePacket filter(ServerPlayer player, ClientboundRecipePacket packet) {
        if (packet.getState() == ClientboundRecipePacket.State.REMOVE) return packet;
        var blocked = RecipeStages.policy(player);
        if (blocked.isEmpty()) return packet;
        List<ResourceLocation> recipes = packet.getRecipes().stream().filter(id -> !blocked.contains(id)).toList();
        List<ResourceLocation> highlights = packet.getHighlights().stream().filter(id -> !blocked.contains(id)).toList();
        if (recipes.size() == packet.getRecipes().size() && highlights.size() == packet.getHighlights().size()) return packet;
        return new ClientboundRecipePacket(packet.getState(), recipes, highlights, packet.getBookSettings());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var manager = player.server.getRecipeManager();
        State current = new State(RecipeStages.policy(player), manager.getRecipes());
        if (current.equals(STATES.put(player.getUUID(), current))) return;
        DecursioStages.NETWORK.sendToPlayer(player, new SyncRecipeVisibilityMessage(current.blocked, Set.of()));
        player.getRecipeBook().sendInitialRecipeBook(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { STATES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); }
    private RecipeVisibilitySync() {}
}
