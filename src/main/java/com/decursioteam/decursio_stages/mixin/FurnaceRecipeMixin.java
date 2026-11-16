package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceRecipeMixin {
    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private static Optional<AbstractCookingRecipe> decstages$recipe(
            RecipeManager.CachedCheck<Container, AbstractCookingRecipe> check, Container container, Level level) {
        Optional<AbstractCookingRecipe> original = check.getRecipeFor(container, level);
        if (original.isEmpty() || !(container instanceof AbstractFurnaceBlockEntity entity)) return original;
        @SuppressWarnings("unchecked") var type = (RecipeType<AbstractCookingRecipe>) original.get().getType();
        return ProcessorRecipes.select(entity, type, new SimpleContainer(entity.getItem(0)), original);
    }

    @Inject(method = "setRecipeUsed", at = @At("HEAD"))
    private void decstages$receipt(Recipe<?> recipe, CallbackInfo ci) {
        ProcessorRecipes.receipt((AbstractFurnaceBlockEntity) (Object) this, recipe);
    }
}
