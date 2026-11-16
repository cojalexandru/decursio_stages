package com.decursioteam.decursio_stages.mixin;
import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(AbstractFurnaceMenu.class)
public abstract class ProcessorBookInputMixin {
    @WrapMethod(method="handlePlacement")
    private RecipeBookMenu.PostPlaceAction decstages$insert(boolean max,boolean drop,RecipeHolder<?> recipe,ServerLevel level,Inventory inventory,Operation<RecipeBookMenu.PostPlaceAction> original) {
        var menu=(AbstractContainerMenu)(Object)this; var before=ProcessorRecipes.inputs(menu);
        try { return original.call(max,drop,recipe,level,inventory); }
        finally { if(inventory.player instanceof ServerPlayer player) ProcessorRecipes.inserted(menu,player,before); }
    }
}
