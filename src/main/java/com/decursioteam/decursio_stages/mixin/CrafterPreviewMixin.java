package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.decursioteam.decursio_stages.recipes.WorkstationRefresh;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(CrafterMenu.class)
public abstract class CrafterPreviewMixin implements WorkstationRefresh {
    @Shadow @Final private CraftingContainer container;
    @Shadow protected abstract void refreshRecipeResult();

    @ModifyExpressionValue(method = "refreshRecipeResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/CrafterBlock;getPotentialResults(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> decstages$preview(Optional<RecipeHolder<CraftingRecipe>> original) {
        return container instanceof CrafterBlockEntity entity
                ? ProcessorRecipes.select(entity, RecipeType.CRAFTING, entity.asCraftInput(), original) : original;
    }

    @Override public void decstages$refresh() { refreshRecipeResult(); }
}
