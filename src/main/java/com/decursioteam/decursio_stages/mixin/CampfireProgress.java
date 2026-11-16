package com.decursioteam.decursio_stages.mixin;

import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CampfireBlockEntity.class)
public interface CampfireProgress {
    @Accessor("cookingProgress") int[] decstages$progress();
    @Accessor("cookingTime") int[] decstages$times();
    @Accessor("quickCheck") RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> decstages$quickCheck();
}
