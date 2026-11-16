package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceOutputMixin extends Slot {
    protected FurnaceOutputMixin(Container container, int slot, int x, int y) { super(container, slot, x, y); }

    @Override public boolean mayPickup(Player player) {
        if (CommonConfig.processorPolicy.get() != CommonConfig.ProcessorPolicy.OUTPUT_ONLY) return true;
        return !(player instanceof ServerPlayer serverPlayer && container instanceof AbstractFurnaceBlockEntity furnace)
                || ProcessorRecipes.mayCollect(serverPlayer, ProcessorRecipes.furnaceReceipts(furnace));
    }
}
