package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@Mixin(RecipeBookMenu.class)
public abstract class ProcessorBookInputMixin {
    @Unique
    private static final ThreadLocal<Deque<Map<BlockEntity, List<ItemStack>>>> DECSTAGES$BEFORE =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void decstages$before(boolean shiftDown, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        if ((Object) this instanceof AbstractFurnaceMenu) {
            DECSTAGES$BEFORE.get().push(ProcessorRecipes.inputs((AbstractContainerMenu) (Object) this));
        }
    }

    @Inject(method = "handlePlacement", at = @At("RETURN"))
    private void decstages$after(boolean shiftDown, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        if ((Object) this instanceof AbstractFurnaceMenu) {
            var stack = DECSTAGES$BEFORE.get();
            if (!stack.isEmpty()) ProcessorRecipes.inserted((AbstractContainerMenu) (Object) this, player, stack.pop());
            if (stack.isEmpty()) DECSTAGES$BEFORE.remove();
        }
    }
}
