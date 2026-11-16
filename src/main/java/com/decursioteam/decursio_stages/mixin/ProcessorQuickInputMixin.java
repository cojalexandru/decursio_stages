package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@Mixin(AbstractFurnaceMenu.class)
public abstract class ProcessorQuickInputMixin {
    @Unique
    private static final ThreadLocal<Deque<Map<BlockEntity, List<ItemStack>>>> DECSTAGES$BEFORE =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void decstages$before(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayer) {
            DECSTAGES$BEFORE.get().push(ProcessorRecipes.inputs((AbstractContainerMenu) (Object) this));
        }
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void decstages$after(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayer serverPlayer) {
            var stack = DECSTAGES$BEFORE.get();
            if (!stack.isEmpty()) ProcessorRecipes.inserted((AbstractContainerMenu) (Object) this, serverPlayer, stack.pop());
            if (stack.isEmpty()) DECSTAGES$BEFORE.remove();
        }
    }
}
