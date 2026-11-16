package com.decursioteam.decursio_stages.datagen;

import java.time.Instant;
import java.util.*;

/** Reload attempts are separate from the active rules; failure information survives rollback reloads. */
public final class RestrictionReloadStatus {
    private static Instant attempted;
    private static Instant failedAt;
    private static boolean successful;
    private static List<String> lastErrors = List.of();
    private RestrictionReloadStatus() {}
    public static synchronized void success() { attempted = Instant.now(); successful = true; }
    public static synchronized void failure(List<String> errors) {
        attempted = failedAt = Instant.now(); successful = false; lastErrors = List.copyOf(errors);
    }
    public static synchronized List<String> report() {
        if (attempted == null) return List.of("No restriction reload has been attempted.");
        List<String> lines = new ArrayList<>();
        lines.add("Last restriction reload: " + (successful ? "SUCCEEDED" : "FAILED — last working rules remain active") + " at " + attempted);
        lines.add("Active rules: " + RestrictionsData.getRegistry().getRestrictions().size() + "; revision: " + RestrictionsData.getRegistry().revision());
        if (!lastErrors.isEmpty()) {
            lines.add((successful ? "Previous failed attempt (a later reload succeeded): " : "Failure details: ") + failedAt);
            lastErrors.forEach(error -> lines.add((successful ? "Previous error: " : "ERROR reload: ") + error));
            lines.add("Fix the listed source and run /decstages reload. Failed attempts do not publish partial rules or removals.");
        }
        return List.copyOf(lines);
    }
}
