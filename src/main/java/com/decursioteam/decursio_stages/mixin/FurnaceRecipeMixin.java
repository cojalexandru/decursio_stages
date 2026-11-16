package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceRecipeMixin {
    @ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Optional;"))
    private static Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> decstages$recipe(Optional<RecipeHolder<AbstractCookingRecipe>> original,
            ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity) {
        if (original.isEmpty()) return original;
        @SuppressWarnings("unchecked") var type = (RecipeType<AbstractCookingRecipe>) original.get().value().getType();
        return ProcessorRecipes.select(entity, type, new SingleRecipeInput(entity.getItem(0)), original);
    }
    @Inject(method = "setRecipeUsed", at = @At("HEAD"))
    private void decstages$receipt(RecipeHolder<?> recipe, CallbackInfo ci) { ProcessorRecipes.receipt((AbstractFurnaceBlockEntity) (Object) this, recipe); }
}
