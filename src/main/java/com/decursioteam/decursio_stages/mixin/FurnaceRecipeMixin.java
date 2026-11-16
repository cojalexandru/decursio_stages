package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceRecipeMixin {
    @ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private static Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> decstages$recipe(Optional<RecipeHolder<AbstractCookingRecipe>> original,
            Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity) {
        if (original.isEmpty()) return original;
        @SuppressWarnings("unchecked") var type = (RecipeType<AbstractCookingRecipe>) original.get().value().getType();
        return ProcessorRecipes.select(entity, type, new SingleRecipeInput(entity.getItem(0)), original);
    }

    @Inject(method = "setRecipeUsed", at = @At("HEAD"))
    private void decstages$receipt(RecipeHolder<?> recipe, CallbackInfo ci) {
        ProcessorRecipes.receipt((AbstractFurnaceBlockEntity) (Object) this, recipe);
    }
}
