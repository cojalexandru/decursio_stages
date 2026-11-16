package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

@EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class MobDebugOverlay {
    @SubscribeEvent
    public static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        var client = Minecraft.getInstance();
        if (!client.getDebugOverlay().showDebugScreen() || client.player == null) return;
        var hit = client.hitResult;
        if (hit == null) hit = client.player.pick(20.0D, 0.0F, false);
        if (hit instanceof EntityHitResult entityHit) {
            event.getRight().add("MobCategory: " + entityHit.getEntity().getType().getCategory().getName());
        }
    }

    private MobDebugOverlay() {}
}
