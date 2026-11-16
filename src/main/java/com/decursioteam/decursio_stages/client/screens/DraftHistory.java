package com.decursioteam.decursio_stages.client.screens;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Local-only snapshots. Text edits coalesce; server acknowledgements establish a new baseline. */
final class DraftHistory {
    private static final int MAX_STATES = 128;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private record State(JsonObject data, long bytes) {}
    private final List<State> states = new ArrayList<>();
    private int cursor;
    private long bytes, lastEdit;
    private String group;

    void reset(JsonObject data) {
        states.clear(); bytes = 0; cursor = 0; endGroup(); append(data);
    }
    void record(JsonObject data, String editGroup) {
        if (states.isEmpty()) { reset(data); return; }
        if (states.get(cursor).data.equals(data)) return;
        long now = System.nanoTime();
        boolean merge = editGroup != null && Objects.equals(group, editGroup)
                && now - lastEdit < 750_000_000L && cursor > 0 && cursor == states.size() - 1;
        while (states.size() > cursor + 1) bytes -= states.removeLast().bytes;
        if (merge) { bytes -= states.removeLast().bytes; cursor--; }
        append(data); cursor = states.size() - 1;
        while (states.size() > 1 && (states.size() > MAX_STATES || bytes > MAX_BYTES)) {
            bytes -= states.removeFirst().bytes; cursor--;
        }
        group = editGroup; lastEdit = now;
    }
    private void append(JsonObject data) {
        JsonObject copy = data.deepCopy(); long size = copy.toString().length() * 2L;
        states.add(new State(copy, size)); bytes += size;
    }
    void endGroup() { group = null; lastEdit = 0; }
    boolean canUndo() { return cursor > 0; }
    boolean canRedo() { return cursor + 1 < states.size(); }
    JsonObject undo() { endGroup(); return states.get(canUndo() ? --cursor : cursor).data.deepCopy(); }
    JsonObject redo() { endGroup(); return states.get(canRedo() ? ++cursor : cursor).data.deepCopy(); }
}
