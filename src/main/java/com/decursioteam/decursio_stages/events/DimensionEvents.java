package com.decursioteam.decursio_stages.events;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

import static com.decursioteam.decursio_stages.utils.ResourceUtil.getApplyToFakePlayer;
import static com.decursioteam.decursio_stages.utils.ResourceUtil.getDimensions;
import static com.decursioteam.decursio_stages.utils.StageUtil.hasStage;

public class DimensionEvents {
    @SubscribeEvent
    public void entityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Registry.getRestrictions().forEach(restriction -> {
                if(!getApplyToFakePlayer(restriction) && player instanceof FakePlayer) return;

                String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage().toLowerCase(Locale.ROOT);
                if (!RestrictionsData.getRestrictionData(restriction).getData().getDimensionList().isEmpty() && !hasStage(player, stage)) {
                    getDimensions(restriction).forEach(dimensionRestriction -> {
                        if (dimensionRestriction.getDimension().equals(event.getDimension().location())) {
                            player.displayClientMessage(Component.literal(dimensionRestriction.getMessage()).withStyle(ChatFormatting.RED), true);
                            event.setCanceled(true);
                        }
                    });
                }
            });
        }
    }
}
