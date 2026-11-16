package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public abstract class SmithingRecipeMixin implements WorkstationRefresh {
    @Unique private ServerPlayer decstages$player;
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
    private void decstages$owner(int id, Inventory inventory, ContainerLevelAccess access, Level level, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player) decstages$player = player;
    }
    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void decstages$result(CallbackInfo ci) {
        if (decstages$player == null) return;
        var menu = (SmithingMenu) (Object) this;
        var input = new SmithingRecipeInput(menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), menu.getSlot(2).getItem());
        var blocked = RecipeStages.policy(decstages$player);
        var recipe = decstages$player.level().getServer().getRecipeManager().recipeMap().getRecipesFor(RecipeType.SMITHING, input, decstages$player.level())
                .filter(r -> !blocked.contains(r.id().identifier())).findFirst();
        var result = (ResultContainer) menu.getSlot(3).container;
        result.setRecipeUsed(recipe.orElse(null));
        result.setItem(0, recipe.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY));
        ci.cancel();
    }
    @Override public void decstages$refresh() { ((SmithingMenu) (Object) this).createResult(); }
}
