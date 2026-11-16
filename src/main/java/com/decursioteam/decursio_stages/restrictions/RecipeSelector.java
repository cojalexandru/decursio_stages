package com.decursioteam.decursio_stages.restrictions;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** Matches a recipe by exact ID, mod namespace, or output item tag. */
public record RecipeSelector(Optional<Identifier> recipe, Optional<String> mod, Optional<Identifier> tag) {
    private static final Codec<RecipeSelector> OBJECT_CODEC = RecordCodecBuilder.<RecipeSelector>create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("recipe").forGetter(RecipeSelector::recipe),
            Codec.STRING.optionalFieldOf("mod").forGetter(RecipeSelector::mod),
            Identifier.CODEC.optionalFieldOf("tag").forGetter(RecipeSelector::tag)
    ).apply(instance, RecipeSelector::new)).validate(selector -> {
        int count = (selector.recipe.isPresent() ? 1 : 0)
                + (selector.mod.isPresent() ? 1 : 0)
                + (selector.tag.isPresent() ? 1 : 0);
        if (count != 1) return DataResult.error(() -> "Recipe selector requires exactly one of recipe, mod, or tag");
        if (selector.mod.isPresent() && !selector.mod.get().matches("[a-z0-9_.-]+"))
            return DataResult.error(() -> "Invalid mod namespace: " + selector.mod.get());
        return DataResult.success(selector);
    });

    public static final Codec<RecipeSelector> CODEC = Codec.either(Identifier.CODEC, OBJECT_CODEC).xmap(
            either -> either.map(RecipeSelector::ofRecipe, selector -> selector),
            selector -> selector.recipe.isPresent() && selector.mod.isEmpty() && selector.tag.isEmpty()
                    ? Either.left(selector.recipe.get()) : Either.right(selector)
    );

    public static RecipeSelector ofRecipe(Identifier id) {
        return new RecipeSelector(Optional.of(id), Optional.empty(), Optional.empty());
    }

    public static RecipeSelector ofMod(String mod) {
        return new RecipeSelector(Optional.empty(), Optional.of(mod), Optional.empty());
    }

    public static RecipeSelector ofTag(Identifier tag) {
        return new RecipeSelector(Optional.empty(), Optional.empty(), Optional.of(tag));
    }

    public String describe() {
        if (recipe.isPresent()) return recipe.get().toString();
        if (mod.isPresent()) return "@" + mod.get();
        return "#" + tag.orElseThrow();
    }

    public boolean matches(Identifier id, List<ItemStack> outputs) {
        if (recipe.isPresent()) return recipe.get().equals(id);
        if (mod.isPresent()) return mod.get().equals(id.getNamespace());
        if (tag.isPresent()) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, tag.get());
            for (ItemStack stack : outputs) if (stack.is(key)) return true;
        }
        return false;
    }
}
