package com.decursioteam.decursio_stages.events;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EntityEvents {
    @SubscribeEvent
    public void entityInteractEvent(PlayerInteractEvent.EntityInteractSpecific event) {
        Player player = event.getEntity();
    }

    @SubscribeEvent
    public void entityDamageEvent(LivingDamageEvent event) {
        if(event.getSource().getEntity() != null){
           //TODO
        }
    }
}
