package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import com.decursioteam.decursio_stages.recipes.WorkstationRefresh;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(SmithingMenu.class)
public abstract class SmithingRecipeMixin implements WorkstationRefresh {
    @Unique private ServerPlayer decstages$player;
    @Shadow @Nullable private SmithingRecipe selectedRecipe;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void decstages$owner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player) decstages$player = player;
    }

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void decstages$result(CallbackInfo ci) {
        if (decstages$player == null) return;
        var menu = (SmithingMenu) (Object) this;
        var input = new SimpleContainer(menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), menu.getSlot(2).getItem());
        var blocked = RecipeStages.policy(decstages$player);
        var recipe = decstages$player.server.getRecipeManager().getRecipesFor(RecipeType.SMITHING, input, decstages$player.level()).stream()
                .filter(r -> !blocked.contains(r.getId())).findFirst();
        var result = (ResultContainer) menu.getSlot(3).container;
        if (recipe.isPresent()) {
            ItemStack assembled = recipe.get().assemble(input, decstages$player.level().registryAccess());
            if (assembled.isItemEnabled(decstages$player.level().enabledFeatures())) {
                this.selectedRecipe = recipe.get();
                result.setRecipeUsed(recipe.get());
                result.setItem(0, assembled);
                ci.cancel();
                return;
            }
        }
        this.selectedRecipe = null;
        result.setRecipeUsed(null);
        result.setItem(0, ItemStack.EMPTY);
        ci.cancel();
    }

    @Override public void decstages$refresh() { ((SmithingMenu) (Object) this).createResult(); }
}
