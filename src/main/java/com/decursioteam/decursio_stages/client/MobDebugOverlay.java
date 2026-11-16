package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class MobDebugOverlay {
    @SubscribeEvent
    public static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        var client = Minecraft.getInstance();
        if (!client.options.renderDebug || client.player == null) return;
        var hit = client.hitResult;
        if (hit == null) hit = client.player.pick(20.0D, 0.0F, false);
        if (hit instanceof EntityHitResult entityHit) {
            event.getRight().add("MobCategory: " + entityHit.getEntity().getType().getCategory().getName());
        }
    }

    private MobDebugOverlay() {}
}
