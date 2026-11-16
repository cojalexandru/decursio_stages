package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.RecipeStages;
import com.decursioteam.decursio_stages.recipes.WorkstationRefresh;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(StonecutterMenu.class)
public abstract class StonecuttingRecipeMixin implements WorkstationRefresh {
    @Unique private ServerPlayer decstages$player;
    @Shadow private List<StonecutterRecipe> recipes;
    @Shadow @Final private DataSlot selectedRecipeIndex;
    @Shadow @Final ResultContainer resultContainer;
    @Shadow @Final Slot resultSlot;
    @Shadow abstract void setupResultSlot();

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void decstages$owner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player) decstages$player = player;
    }

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void decstages$filter(CallbackInfo ci) {
        if (decstages$player == null) return;
        int idx = selectedRecipeIndex.get();
        if (idx >= 0 && idx < recipes.size() && RecipeStages.policy(decstages$player).contains(recipes.get(idx).getId())) {
            resultContainer.setRecipeUsed(null);
            resultSlot.set(ItemStack.EMPTY);
            ((StonecutterMenu) (Object) this).broadcastChanges();
            ci.cancel();
        }
    }

    @Override public void decstages$refresh() { setupResultSlot(); }
}
