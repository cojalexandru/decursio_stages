package com.decursioteam.decursio_stages.recipes;

import com.decursioteam.decursio_stages.datagen.Restrictions;
import com.decursioteam.decursio_stages.restrictions.RecipeSelector;
import com.decursioteam.decursio_stages.utils.StageUtil;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.*;

/** Compiled with a registry snapshot and resolved lazily against the active server RecipeManager. */
public final class RecipeRestrictionIndex {
    public static final RecipeRestrictionIndex EMPTY = new RecipeRestrictionIndex(List.of());

    public record Rule(String stage, boolean appliesToFakePlayers, Set<ResourceLocation> recipes) {}

    private record RuleDef(
            String stage,
            boolean appliesToFakePlayers,
            Set<ResourceLocation> exactIncludes,
            Set<String> modIncludes,
            List<TagKey<Item>> tagIncludes,
            Set<ResourceLocation> exactExceptions,
            Set<String> modExceptions,
            List<TagKey<Item>> tagExceptions
    ) {
        boolean hasDynamicIncludes() { return !modIncludes.isEmpty() || !tagIncludes.isEmpty(); }
        boolean hasDynamicExceptions() { return !modExceptions.isEmpty() || !tagExceptions.isEmpty(); }
        boolean usesTags() { return !tagIncludes.isEmpty() || !tagExceptions.isEmpty(); }
    }

    private static final class Policy {
        private BitSet lockedRules = new BitSet();
        private final Object2IntOpenHashMap<ResourceLocation> counts = new Object2IntOpenHashMap<>();
        private Set<ResourceLocation> blocked = Set.of();
    }

    private final List<RuleDef> definitions;
    private final boolean usesTags;
    private volatile @Nullable Collection<Recipe<?>> compiledRecipes;
    private List<Rule> rules = List.of();
    private Map<ResourceLocation, List<Rule>> byRecipe = Map.of();
    // Values retain neither the player nor the registry, so disconnected player keys can be collected.
    private final Map<ServerPlayer, Policy> policies = new WeakHashMap<>();

    public RecipeRestrictionIndex(Collection<Restrictions> definitions) {
        List<RuleDef> defs = new ArrayList<>();
        boolean anyTags = false;
        for (var definition : definitions) {
            var data = definition.getData();
            if (data.getRecipeList().isEmpty()) continue;

            Set<ResourceLocation> exactIn = new HashSet<>();
            Set<String> modIn = new HashSet<>();
            List<TagKey<Item>> tagIn = new ArrayList<>();
            for (RecipeSelector selector : data.getRecipeList()) {
                selector.recipe().ifPresent(exactIn::add);
                selector.mod().ifPresent(modIn::add);
                selector.tag().ifPresent(id -> tagIn.add(TagKey.create(Registries.ITEM, id)));
            }

            Set<ResourceLocation> exactEx = new HashSet<>();
            Set<String> modEx = new HashSet<>();
            List<TagKey<Item>> tagEx = new ArrayList<>();
            for (RecipeSelector selector : data.getRecipeExceptionList()) {
                selector.recipe().ifPresent(exactEx::add);
                selector.mod().ifPresent(modEx::add);
                selector.tag().ifPresent(id -> tagEx.add(TagKey.create(Registries.ITEM, id)));
            }

            RuleDef def = new RuleDef(
                    data.getStage(),
                    definition.getSettingsCodec().getApplyToFakePlayers(),
                    Set.copyOf(exactIn), Set.copyOf(modIn), List.copyOf(tagIn),
                    Set.copyOf(exactEx), Set.copyOf(modEx), List.copyOf(tagEx)
            );
            anyTags |= def.usesTags();
            defs.add(def);
        }
        this.definitions = List.copyOf(defs);
        this.usesTags = anyTags;
    }

    public static RegistryAccess displayContext(MinecraftServer server) {
        return server.registryAccess();
    }

    public static List<ItemStack> outputs(Recipe<?> recipe, RegistryAccess registries) {
        ItemStack result = recipe.getResultItem(registries);
        return result.isEmpty() ? List.of() : List.of(result);
    }

    public static List<RecipeSelector> matchingSelectors(Collection<RecipeSelector> selectors, ResourceLocation id,
            @Nullable Recipe<?> recipe, @Nullable RegistryAccess registries) {
        if (selectors.isEmpty()) return List.of();
        List<ItemStack> stacks = recipe != null && registries != null ? outputs(recipe, registries) : List.of();
        List<RecipeSelector> matches = new ArrayList<>();
        for (RecipeSelector selector : selectors) {
            if (selector.matches(id, stacks)) matches.add(selector);
        }
        return matches;
    }

    public static boolean matchesAny(Collection<RecipeSelector> selectors, ResourceLocation id,
            @Nullable Recipe<?> recipe, @Nullable RegistryAccess registries) {
        if (selectors.isEmpty()) return false;
        List<ItemStack> stacks = null;
        for (RecipeSelector selector : selectors) {
            if (selector.tag().isPresent()) {
                if (stacks == null) stacks = recipe != null && registries != null ? outputs(recipe, registries) : List.of();
                if (selector.matches(id, stacks)) return true;
            } else if (selector.matches(id, List.of())) {
                return true;
            }
        }
        return false;
    }

    private void ensureCompiled(MinecraftServer server) {
        if (definitions.isEmpty()) return;
        Collection<Recipe<?>> allRecipes = server.getRecipeManager().getRecipes();
        if (compiledRecipes == allRecipes) return;
        synchronized (this) {
            allRecipes = server.getRecipeManager().getRecipes();
            if (compiledRecipes == allRecipes) return;
            Map<ResourceLocation, List<ItemStack>> cachedOutputs = Map.of();
            if (usesTags) {
                RegistryAccess registries = displayContext(server);
                Map<ResourceLocation, List<ItemStack>> outputMap = new HashMap<>(allRecipes.size());
                for (Recipe<?> recipe : allRecipes) {
                    List<ItemStack> stacks = outputs(recipe, registries);
                    if (!stacks.isEmpty()) outputMap.put(recipe.getId(), stacks);
                }
                cachedOutputs = outputMap;
            }

            List<Rule> resolvedRules = new ArrayList<>(definitions.size());
            Map<ResourceLocation, List<Rule>> index = new HashMap<>();
            for (RuleDef def : definitions) {
                Set<ResourceLocation> ids = new HashSet<>(def.exactIncludes);
                if (def.hasDynamicIncludes()) {
                    for (Recipe<?> recipe : allRecipes) {
                        ResourceLocation id = recipe.getId();
                        if (def.modIncludes.contains(id.getNamespace())) {
                            ids.add(id);
                            continue;
                        }
                        if (!def.tagIncludes.isEmpty()) {
                            List<ItemStack> stacks = cachedOutputs.getOrDefault(id, List.of());
                            if (matchesAnyTag(stacks, def.tagIncludes)) ids.add(id);
                        }
                    }
                }
                ids.removeAll(def.exactExceptions);
                if (def.hasDynamicExceptions() && !ids.isEmpty()) {
                    Map<ResourceLocation, List<ItemStack>> outputsRef = cachedOutputs;
                    ids.removeIf(id -> {
                        if (def.modExceptions.contains(id.getNamespace())) return true;
                        if (!def.tagExceptions.isEmpty()) {
                            return matchesAnyTag(outputsRef.getOrDefault(id, List.of()), def.tagExceptions);
                        }
                        return false;
                    });
                }

                Rule rule = new Rule(def.stage, def.appliesToFakePlayers, Set.copyOf(ids));
                resolvedRules.add(rule);
                for (ResourceLocation id : rule.recipes) {
                    index.computeIfAbsent(id, ignored -> new ArrayList<>()).add(rule);
                }
            }
            index.replaceAll((id, matches) -> List.copyOf(matches));
            this.rules = List.copyOf(resolvedRules);
            this.byRecipe = Collections.unmodifiableMap(index);
            this.policies.clear();
            this.compiledRecipes = allRecipes;
        }
    }

    private static boolean matchesAnyTag(List<ItemStack> stacks, List<TagKey<Item>> tags) {
        if (stacks.isEmpty() || tags.isEmpty()) return false;
        for (ItemStack stack : stacks) {
            for (TagKey<Item> tag : tags) {
                if (stack.is(tag)) return true;
            }
        }
        return false;
    }

    public List<Rule> rulesFor(MinecraftServer server, ResourceLocation id) {
        if (definitions.isEmpty()) return List.of();
        ensureCompiled(server);
        return byRecipe.getOrDefault(id, List.of());
    }

    public boolean blocked(ServerPlayer player, ResourceLocation id) {
        boolean fake = player instanceof FakePlayer;
        for (var rule : rulesFor(player.server, id)) {
            if ((!fake || rule.appliesToFakePlayers) && !StageUtil.hasStage(player, rule.stage)) return true;
        }
        return false;
    }

    /** Immutable derived IDs may be reused; every effective stage is evaluated on every call. */
    public Set<ResourceLocation> policy(ServerPlayer player) {
        if (definitions.isEmpty()) return Set.of();
        ensureCompiled(player.server);
        if (rules.isEmpty()) return Set.of();
        var policy = evaluate(player);
        if (policy.blocked == null) policy.blocked = Set.copyOf(policy.counts.keySet());
        return policy.blocked;
    }

    /** Preserve the legacy mutable snapshot API without first allocating an immutable copy. */
    public Set<ResourceLocation> snapshot(ServerPlayer player) {
        if (definitions.isEmpty()) return new HashSet<>();
        ensureCompiled(player.server);
        return rules.isEmpty() ? new HashSet<>() : new HashSet<>(evaluate(player).counts.keySet());
    }

    private Policy evaluate(ServerPlayer player) {
        boolean fake = player instanceof FakePlayer;
        BitSet locked = new BitSet(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            var rule = rules.get(i);
            if ((!fake || rule.appliesToFakePlayers) && !StageUtil.hasStage(player, rule.stage)) locked.set(i);
        }
        var policy = policies.computeIfAbsent(player, ignored -> new Policy());
        if (policy.lockedRules.equals(locked)) return policy;
        BitSet changed = (BitSet) policy.lockedRules.clone(); changed.xor(locked);
        boolean membershipChanged = false;
        for (int i = changed.nextSetBit(0); i >= 0; i = changed.nextSetBit(i + 1)) {
            int delta = locked.get(i) ? 1 : -1;
            for (var id : rules.get(i).recipes) {
                int before = policy.counts.getInt(id), after = before + delta;
                if (after == 0) policy.counts.removeInt(id); else policy.counts.put(id, after);
                membershipChanged |= before == 0 || after == 0;
            }
        }
        policy.lockedRules = locked;
        if (membershipChanged && (policy.blocked == null || !policy.counts.keySet().equals(policy.blocked))) policy.blocked = null;
        return policy;
    }

    public void forget(ServerPlayer player) { policies.remove(player); }
    public void clearPlayers() { policies.clear(); }
}
