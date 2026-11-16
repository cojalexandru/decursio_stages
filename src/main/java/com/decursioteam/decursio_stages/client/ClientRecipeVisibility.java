package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.network.messages.SyncRecipeVisibilityMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class ClientRecipeVisibility {
    private static Set<ResourceLocation> blocked = Set.of();
    private static Set<Integer> displays = Set.of();
    private static long revision, catalogRevision;

    public static boolean hiddenDisplay(int id) { return displays.contains(id); }
    public static long catalogRevision() { return catalogRevision; }
    public static Set<ResourceLocation> blocked() { return blocked; }
    public static long revision() { return revision; }

    public static void apply(SyncRecipeVisibilityMessage message) {
        apply(message.blocked(), message.displays());
    }

    public static void apply(Set<ResourceLocation> ids, Set<Integer> displayIds) {
        blocked = Set.copyOf(ids);
        displays = Set.copyOf(displayIds);
        revision++;
    }

    @SubscribeEvent public static void recipes(RecipesUpdatedEvent event) { revision++; catalogRevision++; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { apply(Set.of(), Set.of()); }

    private ClientRecipeVisibility() {}
}
