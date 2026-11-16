package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;

@Mixin(StonecutterMenu.class)
public abstract class StonecuttingRecipeMixin implements WorkstationRefresh {
    @Unique private ServerPlayer decstages$player;
    @Shadow protected abstract void setupResultSlot(int index);
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void decstages$owner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player) decstages$player = player;
    }
    @ModifyExpressionValue(method = "setupResultSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/SelectableRecipe;recipe()Ljava/util/Optional;"))
    private Optional<RecipeHolder<StonecutterRecipe>> decstages$filter(Optional<RecipeHolder<StonecutterRecipe>> recipe) {
        return decstages$player == null ? recipe : recipe.filter(r -> !RecipeStages.policy(decstages$player).contains(r.id().identifier()));
    }
    @Override public void decstages$refresh() { setupResultSlot(((StonecutterMenu) (Object) this).getSelectedRecipeIndex()); }
}
