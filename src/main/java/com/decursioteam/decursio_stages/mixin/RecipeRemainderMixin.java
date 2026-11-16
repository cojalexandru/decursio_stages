package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ResultSlot.class)
public abstract class RecipeRemainderMixin {
    @Shadow @Final private Player player;
    @Unique private RecipeHolder<?> decstages$usedRecipe;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void decstages$capture(Player player, ItemStack stack, CallbackInfo ci) {
        var used = ((Slot) (Object) this).container instanceof ResultContainer result ? result.getRecipeUsed() : null;
        if (used != null) decstages$usedRecipe = used;
    }

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void decstages$beforeAward(ItemStack stack, CallbackInfo ci) {
        // Shift-click awards (and clears) the recipe before onTake is invoked.
        var used = ((Slot) (Object) this).container instanceof ResultContainer result ? result.getRecipeUsed() : null;
        if (used != null) decstages$usedRecipe = used;
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void decstages$clear(Player player, ItemStack stack, CallbackInfo ci) {
        decstages$usedRecipe = null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRemainingItemsFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;"))
    private NonNullList<ItemStack> decstages$remaining(RecipeManager manager, RecipeType type, RecipeInput input, Level level) {
        if (player instanceof ServerPlayer serverPlayer && input instanceof CraftingInput craftInput) {
            Optional<RecipeHolder<CraftingRecipe>> preferred = manager.getRecipeFor(RecipeType.CRAFTING, craftInput, level);
            if (decstages$usedRecipe != null && decstages$usedRecipe.value() instanceof CraftingRecipe crafting && crafting.matches(craftInput, level)) {
                preferred = Optional.of(new RecipeHolder<>(decstages$usedRecipe.id(), crafting));
            }
            var selected = RecipeStages.select(serverPlayer, craftInput, preferred);
            if (selected.isPresent()) return selected.get().value().getRemainingItems(craftInput);
        }
        return manager.getRemainingItemsFor((RecipeType<Recipe<RecipeInput>>) type, input, level);
    }
}
