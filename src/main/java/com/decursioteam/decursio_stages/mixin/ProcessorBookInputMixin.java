package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RecipeBookMenu.class)
public abstract class ProcessorBookInputMixin {
    @WrapMethod(method = "handlePlacement")
    private void decstages$insert(boolean shiftDown, RecipeHolder<?> recipe, ServerPlayer player, Operation<Void> original) {
        if (!((Object) this instanceof AbstractFurnaceMenu)) {
            original.call(shiftDown, recipe, player);
            return;
        }
        var menu = (AbstractContainerMenu) (Object) this;
        var before = ProcessorRecipes.inputs(menu);
        try { original.call(shiftDown, recipe, player); }
        finally { ProcessorRecipes.inserted(menu, player, before); }
    }
}
