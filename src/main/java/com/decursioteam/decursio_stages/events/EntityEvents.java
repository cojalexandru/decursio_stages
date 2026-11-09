package com.decursioteam.decursio_stages.events;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class EntityEvents {
    @SubscribeEvent
    public void entityInteractEvent(PlayerInteractEvent.EntityInteractSpecific event) {
        Player player = event.getEntity();
    }

    @SubscribeEvent
    public void entityDamageEvent(LivingDamageEvent.Post event) {
        if(event.getSource().getEntity() != null){
           //TODO
        }
    }
}
