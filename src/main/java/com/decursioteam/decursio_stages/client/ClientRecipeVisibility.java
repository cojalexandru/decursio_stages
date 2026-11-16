package com.decursioteam.decursio_stages.client;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import java.util.Set;

@EventBusSubscriber(modid = "decursio_stages", value = Dist.CLIENT)
public final class ClientRecipeVisibility {
    private static Set<Identifier> blocked = Set.of();
    private static Set<Integer> displays = Set.of();
    public static boolean hiddenDisplay(int id) { return displays.contains(id); }
    private static long revision, catalogRevision;
    public static long catalogRevision() { return catalogRevision; }
    public static Set<Identifier> blocked() { return blocked; }
    public static long revision() { return revision; }
    public static void apply(Set<Identifier> ids, Set<Integer> displayIds) { blocked = Set.copyOf(ids); displays = Set.copyOf(displayIds); revision++; }
    @SubscribeEvent public static void recipes(RecipesReceivedEvent event) { revision++; catalogRevision++; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { apply(Set.of(), Set.of()); }
    private ClientRecipeVisibility() {}
}
