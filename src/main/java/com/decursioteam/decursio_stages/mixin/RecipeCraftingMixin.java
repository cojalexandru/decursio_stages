package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(CraftingMenu.class)
public abstract class RecipeCraftingMixin {
    @ModifyExpressionValue(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;"))
    private static Optional<RecipeHolder<CraftingRecipe>> decstages$select(Optional<RecipeHolder<CraftingRecipe>> original,
            AbstractContainerMenu menu, Level level, Player player, CraftingContainer input, ResultContainer result, RecipeHolder<CraftingRecipe> hint) {
        if (!(player instanceof ServerPlayer serverPlayer)) return original;
        // Preserve a valid recipe-book choice when refreshing a result with unchanged inputs.
        var used = result.getRecipeUsed();
        if (hint == null && used != null && used.value() instanceof CraftingRecipe crafting && crafting.matches(input.asCraftInput(), level)) {
            original = Optional.of(new RecipeHolder<>(used.id(), crafting));
        }
        return RecipeStages.select(serverPlayer, input.asCraftInput(), original);
    }
}
