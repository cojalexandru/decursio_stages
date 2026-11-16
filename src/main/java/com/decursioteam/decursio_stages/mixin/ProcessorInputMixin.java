package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(AbstractContainerMenu.class)
public abstract class ProcessorInputMixin {
    @Unique private Map<BlockEntity, List<ItemStack>> decstages$before = Map.of();

    @Inject(method = "clicked", at = @At("HEAD"))
    private void decstages$before(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer) decstages$before = ProcessorRecipes.inputs((AbstractContainerMenu) (Object) this);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void decstages$after(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) ProcessorRecipes.inserted((AbstractContainerMenu) (Object) this, serverPlayer, decstages$before);
        decstages$before = Map.of();
    }
}
