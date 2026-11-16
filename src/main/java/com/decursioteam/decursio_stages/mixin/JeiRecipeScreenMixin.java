package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.client.RecipeScreenRefresh;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Re-run the current search so cached open layouts cannot retain a newly hidden recipe. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public abstract class JeiRecipeScreenMixin implements RecipeScreenRefresh {
    @Shadow(remap = false) private Screen parentScreen;
    @Unique private List<IFocus<?>> decstages$focus = List.of();
    @Unique private List<RecipeType<?>> decstages$types = List.of();

    @Inject(method = "show(Ljava/util/List;)V", at = @At("HEAD"))
    private void decstages$focus(List<IFocus<?>> focuses, CallbackInfo ci) {
        decstages$focus = List.copyOf(focuses);
        decstages$types = List.of();
    }

    @Inject(method = "showTypes", at = @At("HEAD"))
    private void decstages$types(List<RecipeType<?>> types, CallbackInfo ci) {
        decstages$types = List.copyOf(types);
        decstages$focus = List.of();
    }

    @Override
    public void decstages$refreshRecipes() {
        IRecipesGui gui = (IRecipesGui) this;
        Minecraft.getInstance().setScreen(parentScreen);
        if (decstages$types.isEmpty()) gui.show(decstages$focus);
        else gui.showTypes(decstages$types);
    }
}
