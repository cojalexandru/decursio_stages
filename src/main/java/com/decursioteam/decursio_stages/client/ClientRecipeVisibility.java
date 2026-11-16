package com.decursioteam.decursio_stages.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;

import java.util.Set;

@EventBusSubscriber(modid = "decursio_stages", value = Dist.CLIENT)
public final class ClientRecipeVisibility {
    private static Set<ResourceLocation> blocked = Set.of();
    private static Set<Integer> displays = Set.of();
    private static long revision, catalogRevision;

    public static boolean hiddenDisplay(int id) { return displays.contains(id); }
    public static long catalogRevision() { return catalogRevision; }
    public static Set<ResourceLocation> blocked() { return blocked; }
    public static long revision() { return revision; }

    public static void apply(Set<ResourceLocation> ids, Set<Integer> displayIds) {
        blocked = Set.copyOf(ids);
        displays = Set.copyOf(displayIds);
        revision++;
    }

    @SubscribeEvent public static void recipes(RecipesUpdatedEvent event) { revision++; catalogRevision++; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { apply(Set.of(), Set.of()); }

    private ClientRecipeVisibility() {}
}
