package com.decursioteam.decursio_stages.events;

import net.neoforged.bus.api.Event;
import java.util.Set;

/** Client-side notification after a complete, validated restriction update is published. */
public final class RestrictionsChangedEvent extends Event {
    private final Set<String> changed;
    private final boolean full;
    public RestrictionsChangedEvent(Set<String> changed, boolean full) { this.changed = Set.copyOf(changed); this.full = full; }
    public Set<String> changed() { return changed; }
    public boolean full() { return full; }
}
