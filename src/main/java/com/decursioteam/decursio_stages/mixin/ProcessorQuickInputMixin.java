package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({AbstractFurnaceMenu.class, CrafterMenu.class})
public abstract class ProcessorQuickInputMixin {
    @WrapMethod(method = "quickMoveStack")
    private ItemStack decstages$insert(Player player, int slot, Operation<ItemStack> original) {
        var menu = (AbstractContainerMenu) (Object) this;
        var before = ProcessorRecipes.inputs(menu);
        try { return original.call(player, slot); }
        finally { if (player instanceof ServerPlayer serverPlayer) ProcessorRecipes.inserted(menu, serverPlayer, before); }
    }
}
