package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.network.messages.SyncRestrictionsMessage;
import java.util.function.Consumer;

/** Revision/recovery state independent of the client transport and rendering lifecycle. */
public final class RestrictionSyncState {
    public enum Result { APPLIED, IGNORED, REQUEST_SNAPSHOT }
    private static final int RETRY_TICKS = 100;
    private long revision = -1;
    private boolean recoveryPending;
    private int recoveryTicks;

    public long revision() { return revision; }

    public Result apply(SyncRestrictionsMessage message, Consumer<SyncRestrictionsMessage> apply) {
        if (message.revision() < revision || (!message.full() && message.revision() == revision)) return Result.IGNORED;
        if (!message.full() && (recoveryPending || message.baseRevision() != revision))
            return recover() ? Result.REQUEST_SNAPSHOT : Result.IGNORED;
        // A failed parse/publication must not advance the revision or clear recovery.
        apply.accept(message);
        revision = message.revision();
        recoveryPending = false;
        recoveryTicks = 0;
        return Result.APPLIED;
    }

    public boolean recover() {
        if (recoveryPending) return false;
        recoveryPending = true;
        recoveryTicks = RETRY_TICKS;
        return true;
    }

    /** Called only while connected. */
    public boolean retry() {
        if (!recoveryPending || --recoveryTicks > 0) return false;
        recoveryTicks = RETRY_TICKS;
        return true;
    }

    public void reset() { revision = -1; recoveryPending = false; recoveryTicks = 0; }
}
