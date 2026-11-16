package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Optional;

@Mixin(CraftingMenu.class)
public abstract class RecipeCraftingMixin {
    @ModifyExpressionValue(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;"))
    private static Optional<RecipeHolder<CraftingRecipe>> decstages$select(Optional<RecipeHolder<CraftingRecipe>> original,
            AbstractContainerMenu menu, ServerLevel level, Player player, CraftingContainer input, ResultContainer result, RecipeHolder<CraftingRecipe> hint) {
        // Preserve a valid recipe-book choice when refreshing a result with unchanged inputs.
        var used = result.getRecipeUsed();
        if (hint == null && used != null && used.value() instanceof CraftingRecipe crafting && crafting.matches(input.asCraftInput(), level))
            original = Optional.of(new RecipeHolder<>(used.id(), crafting));
        return RecipeStages.select((ServerPlayer) player, input.asCraftInput(), original);
    }
}
