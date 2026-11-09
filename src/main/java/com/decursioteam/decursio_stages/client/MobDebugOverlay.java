package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.DecursioStages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;

@EventBusSubscriber(modid = DecursioStages.MOD_ID, value = Dist.CLIENT)
public final class MobDebugOverlay {
    @SubscribeEvent
    public static void register(RegisterDebugEntriesEvent event) {
        var id = Identifier.fromNamespaceAndPath(DecursioStages.MOD_ID, "mob_category");
        event.register(id, (output, level, clientChunk, serverChunk) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            var hit = client.hitResult;
            if (hit == null) hit = client.player.pick(20.0D, 0.0F, false);
            if (hit instanceof EntityHitResult entityHit)
                output.addLine("MobCategory: " + entityHit.getEntity().getType().getCategory().getName());
        });
        event.includeInProfile(id, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
    }
}
