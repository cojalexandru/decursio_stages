package com.decursioteam.decursio_stages.recipes;

import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.function.Consumer;

/** Tracks only this mod's visibility changes; ingredients are never modified. */
public final class RecipeVisibilityIndex<T> {
    public record Entry<T>(T value, Set<Identifier> sources) {
        public Entry { sources = Set.copyOf(sources); }
    }
    private final List<Entry<T>> entries;
    private final Set<T> hidden = new HashSet<>();
    public RecipeVisibilityIndex(List<Entry<T>> entries) { this.entries = List.copyOf(entries); }
    public static boolean hidden(Collection<Identifier> sources, Set<Identifier> blocked) {
        return !sources.isEmpty() && blocked.containsAll(sources);
    }
    public void update(Set<Identifier> blocked, Consumer<List<T>> hide, Consumer<List<T>> show) {
        Set<T> next = new HashSet<>();
        for (var entry : entries) if (hidden(entry.sources, blocked)) next.add(entry.value);
        List<T> removed = hidden.stream().filter(value -> !next.contains(value)).toList();
        List<T> added = next.stream().filter(value -> !hidden.contains(value)).toList();
        if (!removed.isEmpty()) show.accept(removed);
        if (!added.isEmpty()) hide.accept(added);
        hidden.clear(); hidden.addAll(next);
    }
}
