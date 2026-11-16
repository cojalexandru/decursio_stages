package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMixin {
    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true)
    private static void decstages$cook(Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
        if (level.isClientSide) return;
        CampfireProgress access = (CampfireProgress) entity;
        var cache = access.decstages$quickCheck();
        boolean changed = false;
        for (int i = 0; i < entity.getItems().size(); i++) {
            var stack = entity.getItems().get(i);
            if (stack.isEmpty()) continue;
            var input = new SingleRecipeInput(stack);
            var recipe = ProcessorRecipes.select(entity, RecipeType.CAMPFIRE_COOKING, input, cache.getRecipeFor(input, level));
            if (recipe.isEmpty()) continue;
            changed = true;
            access.decstages$times()[i] = recipe.get().value().getCookingTime();
            if (++access.decstages$progress()[i] >= access.decstages$times()[i]) {
                ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
                if (result.isItemEnabled(level.enabledFeatures())) {
                    ProcessorRecipes.withDrops(recipe.get().id().toString(),
                            () -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), result));
                    entity.getItems().set(i, ItemStack.EMPTY);
                    level.sendBlockUpdated(pos, state, state, 3);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
                }
            }
        }
        if (changed) entity.setChanged();
        ci.cancel();
    }

    @Inject(method = "placeFood", at = @At("RETURN"))
    private void decstages$insert(LivingEntity source, ItemStack stack, int cookTime, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValue() && source instanceof ServerPlayer player) {
            ProcessorRecipes.assign((CampfireBlockEntity) (Object) this, player, false);
        }
    }
}
