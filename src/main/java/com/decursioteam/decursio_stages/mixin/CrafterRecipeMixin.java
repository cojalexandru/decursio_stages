package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.Optional;

@Mixin(CrafterBlock.class)
public abstract class CrafterRecipeMixin {
    @ModifyExpressionValue(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/CrafterBlock;getPotentialResults(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> decstages$recipe(Optional<RecipeHolder<CraftingRecipe>> original, BlockState state, ServerLevel level, BlockPos pos) {
        var entity = (CrafterBlockEntity) level.getBlockEntity(pos);
        return ProcessorRecipes.select(entity, RecipeType.CRAFTING, entity.asCraftInput(), original);
    }
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "dispenseItem")
    private void decstages$drops(ServerLevel level, BlockPos pos, CrafterBlockEntity entity, net.minecraft.world.item.ItemStack stack,
            BlockState state, RecipeHolder<?> recipe, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        ProcessorRecipes.withDrops(recipe.id().identifier().toString(), () -> original.call(level, pos, entity, stack, state, recipe));
    }
}
