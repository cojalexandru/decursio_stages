package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    private void decstages$clear(Player player, ItemStack stack, CallbackInfo ci) { decstages$usedRecipe = null; }
    @Inject(method = "getRemainingItems", at = @At("HEAD"), cancellable = true)
    private void decstages$remaining(CraftingInput input, Level level, CallbackInfoReturnable<NonNullList<ItemStack>> ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            var preferred = serverPlayer.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (decstages$usedRecipe != null && decstages$usedRecipe.value() instanceof CraftingRecipe crafting && crafting.matches(input, level)) {
                preferred = java.util.Optional.of(new RecipeHolder<>(decstages$usedRecipe.id(), crafting));
            }
            var selected = RecipeStages.select(serverPlayer, input, preferred);
            if (selected.isPresent()) ci.setReturnValue(selected.get().value().getRemainingItems(input));
        }
    }
}
