package com.decursioteam.decursio_stages.client;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.*;
import java.util.function.*;

/** Viewer-independent indices and overlapping-rule ownership. Catalog entries preserve NBT variants. */
public final class IngredientVisibility<T> {
    private record Ingredient<T>(T value, ItemStack item, Fluid fluid) {}
    private final List<Ingredient<T>> ingredients = new ArrayList<>();
    private final Map<ResourceLocation, Set<Integer>> items = new HashMap<>();
    private final Map<String, Set<Integer>> mods = new HashMap<>();
    private final Map<ResourceLocation, Set<Integer>> itemTags = new HashMap<>(), fluidTags = new HashMap<>(), fluidFamilies = new HashMap<>();
    private final Map<Fluid, Set<Integer>> fluids = new HashMap<>();
    private final Map<String, Set<Integer>> matches = new HashMap<>();
    private final Map<String, List<JsonElement>> signatures = new HashMap<>();
    private final Set<String> active = new HashSet<>();
    private final int[] counts;
    private final Consumer<List<T>> show, hide;

    public IngredientVisibility(Collection<T> catalog, Function<T, ItemStack> item, Function<T, Fluid> fluid,
                                Consumer<List<T>> show, Consumer<List<T>> hide) {
        this.show = show; this.hide = hide;
        for (T value : catalog) {
            ItemStack stack = item.apply(value);
            Fluid liquid = fluid.apply(value);
            if ((stack == null || stack.isEmpty()) && liquid == null) continue;
            int index = ingredients.size();
            ingredients.add(new Ingredient<>(value, stack == null ? null : stack.copy(), liquid));
            ResourceLocation id;
            if (liquid != null) {
                fluids.computeIfAbsent(liquid, ignored -> new HashSet<>()).add(index);
                id = BuiltInRegistries.FLUID.getKey(liquid);
            } else {
                id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                items.computeIfAbsent(id, ignored -> new HashSet<>()).add(index);
            }
            mods.computeIfAbsent(id.getNamespace(), ignored -> new HashSet<>()).add(index);
        }
        counts = new int[ingredients.size()];
    }

    public void update(RestrictionsData registry, Predicate<String> hasStage, Set<String> changed, boolean invalidateAll) {
        update(registry, hasStage, changed, invalidateAll, true);
    }

    public void update(RestrictionsData registry, Predicate<String> hasStage, Set<String> changed, boolean invalidateAll, boolean evaluateAll) {
        if (invalidateAll) { itemTags.clear(); fluidTags.clear(); fluidFamilies.clear(); }
        Map<Integer, Boolean> before = new HashMap<>();
        Set<String> ids = new HashSet<>(changed);
        if (evaluateAll || invalidateAll) {
            ids.addAll(registry.getRestrictions().keySet());
            ids.addAll(active);
            ids.addAll(signatures.keySet());
        }
        for (String id : ids) {
            var restriction = registry.getRestrictions().get(id);
            List<JsonElement> signature = restriction == null ? List.of()
                    : changed.contains(id) || !signatures.containsKey(id) ? signature(registry.getRawRestrictionData(id)) : signatures.get(id);
            boolean invalid = invalidateAll || !signature.equals(signatures.get(id));
            boolean wasActive = active.contains(id);
            boolean nowActive = restriction != null && restriction.getSettingsCodec().getHideInJEI()
                    && !hasStage.test(restriction.getData().getStage());
            if (wasActive && (invalid || !nowActive)) adjust(matches.getOrDefault(id, Set.of()), -1, before);
            if (invalid) { matches.remove(id); signatures.put(id, signature); }
            if (nowActive && (invalid || !wasActive)) adjust(matches.computeIfAbsent(id, key -> collect(registry, key)), 1, before);
            if (nowActive) active.add(id); else active.remove(id);
            if (restriction == null) { matches.remove(id); signatures.remove(id); }
        }
        List<T> revealed = new ArrayList<>(), hidden = new ArrayList<>();
        before.forEach((index, previouslyHidden) -> {
            boolean nowHidden = counts[index] > 0;
            if (previouslyHidden != nowHidden) (nowHidden ? hidden : revealed).add(ingredients.get(index).value);
        });
        if (!revealed.isEmpty()) show.accept(revealed);
        if (!hidden.isEmpty()) hide.accept(hidden);
    }

    private void adjust(Set<Integer> indices, int delta, Map<Integer, Boolean> before) {
        for (int index : indices) {
            before.putIfAbsent(index, counts[index] > 0);
            counts[index] += delta;
        }
    }

    private static List<JsonElement> signature(JsonObject root) {
        var data = root.getAsJsonObject("Restriction Data");
        List<JsonElement> result = new ArrayList<>();
        for (String key : List.of("itemList", "tagList", "modList", "fluidList", "exceptionList"))
            result.add(data.has(key) ? data.get(key) : JsonNull.INSTANCE);
        return List.copyOf(result);
    }

    private Set<Integer> collect(RestrictionsData registry, String id) {
        var data = registry.getRestrictions().get(id).getData();
        Set<Integer> candidates = new HashSet<>();
        for (var rule : data.getItemList()) candidates.addAll(items.getOrDefault(rule.getResourceLocation(), Set.of()));
        for (var tag : data.getTagList()) candidates.addAll(itemTags.computeIfAbsent(tag, this::itemTag));
        for (var mod : data.getModList()) candidates.addAll(mods.getOrDefault(mod, Set.of()));
        for (var rule : data.getFluidList()) {
            if (rule.getFluid() != null) candidates.addAll(fluidFamilies.computeIfAbsent(rule.getResourceLocation(), ignored -> {
                Set<Integer> family = new HashSet<>();
                fluids.forEach((fluid, indices) -> { if (fluid.isSame(rule.getFluid())) family.addAll(indices); });
                return family;
            }));
            else if (rule.getTag() != null) candidates.addAll(fluidTags.computeIfAbsent(rule.getTag(), this::fluidTag));
            else if (rule.getMod() != null) candidates.addAll(mods.getOrDefault(rule.getMod(), Set.of()));
        }
        candidates.removeIf(index -> {
            var ingredient = ingredients.get(index);
            return ingredient.fluid != null ? !ResourceUtil.isFluidRestricted(id, ingredient.fluid)
                    : !registry.compiled(id).matches(ingredient.item, ResourceUtil.CHECK_TYPES.ALL);
        });
        return Set.copyOf(candidates);
    }

    private Set<Integer> itemTag(ResourceLocation id) {
        Set<Integer> indices = new HashSet<>();
        var tag = TagKey.create(Registries.ITEM, id);
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).item != null && ingredients.get(i).item.is(tag)) indices.add(i);
        }
        return Set.copyOf(indices);
    }

    private Set<Integer> fluidTag(ResourceLocation id) {
        Set<Integer> indices = new HashSet<>();
        var tag = TagKey.create(Registries.FLUID, id);
        fluids.forEach((fluid, values) -> { if (fluid.is(tag)) indices.addAll(values); });
        return Set.copyOf(indices);
    }

    public void restore() {
        List<T> values = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) if (counts[i] > 0) values.add(ingredients.get(i).value);
        if (!values.isEmpty()) show.accept(values);
        Arrays.fill(counts, 0); active.clear(); matches.clear(); signatures.clear();
    }
}
