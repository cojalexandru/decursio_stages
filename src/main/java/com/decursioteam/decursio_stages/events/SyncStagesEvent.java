package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class SyncStagesEvent extends PlayerEvent {

    private final IStagesData data;

    public SyncStagesEvent(IStagesData data) {

        this(data, Minecraft.getInstance().player);
    }

    public SyncStagesEvent(IStagesData data, Player player) {

        super(player);
        this.data = data;
    }

    public IStagesData getData () {

        return this.data;
    }
}
