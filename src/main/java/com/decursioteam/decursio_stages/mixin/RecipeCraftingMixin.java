package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Optional;

@Mixin(CraftingMenu.class)
public abstract class RecipeCraftingMixin {
    @ModifyVariable(method = "slotChangedCraftingGrid", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private static Optional<CraftingRecipe> decstages$select(Optional<CraftingRecipe> original,
            AbstractContainerMenu menu, Level level, Player player, CraftingContainer input, ResultContainer result) {
        if (!(player instanceof ServerPlayer serverPlayer)) return original;
        var used = result.getRecipeUsed();
        if (used instanceof CraftingRecipe crafting && crafting.matches(input, level)) {
            original = Optional.of(crafting);
        }
        return RecipeStages.select(serverPlayer, input, original);
    }
}
