package com.decursioteam.decursio_stages.client.screens;

import com.decursioteam.decursio_stages.mobstaging.MobRestriction;
import com.decursioteam.decursio_stages.restrictions.DimensionRestriction;
import com.decursioteam.decursio_stages.restrictions.StructureRestriction;
import com.decursioteam.decursio_stages.utils.ItemDataUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static com.decursioteam.decursio_stages.client.screens.RestrictionForm.*;
import static com.decursioteam.decursio_stages.client.screens.RestrictionEditorTheme.*;

/** Form drafts stay local until Apply; the parent screen owns the server save. */
public final class AdvancedRestrictionScreen extends Screen {
    private static final List<Codec<?>> CODECS = List.of(
            com.decursioteam.decursio_stages.restrictions.RecipeSelector.CODEC.listOf(),
            com.decursioteam.decursio_stages.restrictions.RecipeSelector.CODEC.listOf(),
            StructureRestriction.codec().listOf(),
            DimensionRestriction.codec().listOf(), MobRestriction.codec().listOf(), Codec.STRING.listOf(),
            com.decursioteam.decursio_stages.restrictions.BlockReplacement.CODEC.listOf());
    private static final class View {
        final Field field;
        final JsonElement value;
        final String title;
        final List<Object> path;
        int page;

        View(Field field, JsonElement value, String title, List<Object> path) {
            this.field = field;
            this.value = value;
            this.title = title;
            this.path = List.copyOf(path);
        }
    }

    private record Label(String text, int x, int y, int width) {}
    private final RestrictEditorScreen parent;
    private final Consumer<JsonObject> apply;
    private JsonObject draft = new JsonObject();
    private final JsonObject original;
    private final DraftHistory history = new DraftHistory();
    private final List<EditorValidation> validation = new ArrayList<>();
    private final java.util.Map<String, EditBox> editFields = new java.util.HashMap<>();
    private Button undoButton, redoButton, applyButton;
    private String firstError;
    private final Deque<View> views = new ArrayDeque<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<SearchableIdBox> idBoxes = new ArrayList<>();
    private final java.util.Map<String, List<SearchableIdBox.Option>> idOptions = new java.util.HashMap<>();
    private int selected;
    private int left;
    private int panelWidth;
    private int panelHeight;
    private int top;
    private int rows;
    private String status = "";
    private boolean confirmDiscard;
    private static final int SIDEBAR_ROW = 20;
    private int sidebarWidth, sidebarScroll, sidebarRows;
    private EditBox entrySearch;
    private String search = "";
    private final List<Integer> sidebarEntries = new ArrayList<>();

    public AdvancedRestrictionScreen(RestrictEditorScreen parent, JsonObject data, Consumer<JsonObject> apply) {
        super(Component.literal("Advanced Restrictions"));
        this.parent = parent;
        this.apply = apply;
        for (Field section : SECTIONS) draft.add(section.key(), data.has(section.key())
                ? data.get(section.key()).deepCopy() : new JsonArray());
        for (String key : List.of("recipeList", "recipeExceptionList")) {
            if (draft.get(key) instanceof JsonArray array) {
                for (int i = 0; i < array.size(); i++) {
                    JsonElement entry = array.get(i);
                    if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                        JsonObject wrapped = new JsonObject();
                        wrapped.addProperty("recipe", entry.getAsString());
                        array.set(i, wrapped);
                    }
                }
            }
        }
        original = draft.deepCopy();
        history.reset(draft);
        selectSection(0);
    }

    public RestrictEditorScreen parent() { return parent; }

    private void selectSection(int index) {
        if (selected != index) { search = ""; sidebarScroll = 0; }
        selected = index;
        views.clear();
        Field section = SECTIONS.get(index);
        views.push(new View(section, draft.get(section.key()), section.label(), List.of(section.key())));
    }

    @Override
    protected void init() {
        labels.clear();
        validation.clear();
        editFields.clear();
        idBoxes.clear();
        sidebarWidth = Math.max(100, Math.min(260, width / 3));
        left = sidebarWidth + 20;
        top = 8;
        panelWidth = width - left - 12;
        panelHeight = height - 16;
        rows = Math.max(1, (panelHeight - 174) / 32);
        for (int i = 0; i < SECTIONS.size(); i++) {
            final int tab = i;
            Field section = SECTIONS.get(i);
            Button button = button(section.label(), 12 + i * ((width - 24) / SECTIONS.size()), top + 28, (width - 24) / SECTIONS.size() - 3, () -> {
                selectSection(tab);
                navigate();
            });
            button.active = i != selected;
        }
        initSidebar();
        View view = views.peek();
        int toolbarY = top + 58;
        button("Back", left, toolbarY, 60, () -> { views.pop(); navigate(); }).active = views.size() > 1;
        boolean list = view.value.isJsonArray();
        boolean entry = !list && view.path.getLast() instanceof Integer;
        int toolsWidth = list ? 88 : entry ? 132 : 0;
        label(view.title, left + 68, toolbarY + 6, Math.max(0, panelWidth - 68 - toolsWidth));
        if (entry) {
            button("Copy", left + panelWidth - 124, toolbarY, 60, () -> copyCurrentEntry(view));
            button("Remove", left + panelWidth - 60, toolbarY, 60, () -> removeCurrentEntry(view));
        }
        if (list) button("+ Add", left + panelWidth - 80, toolbarY, 80, () -> addEntry(view));
        int count = list ? view.value.getAsJsonArray().size() : view.field.children().size();
        int pages = Math.max(1, (count + rows - 1) / rows);
        view.page = Math.min(view.page, pages - 1);
        int first = view.page * rows;
        for (int i = first; i < Math.min(first + rows, count); i++) {
            int y = top + 84 + (i - first) * 32;
            if (list) listRow(view, i, y);
            else fieldRow(view, view.field.children().get(i), y);
        }
        if (count == 0) label("No entries. Click + Add to create one.", left, top + 90, panelWidth);
        button("<", left, top + panelHeight - 80, 30, () -> { view.page--; navigate(); }).active = view.page > 0;
        button(">", left + panelWidth - 30, top + panelHeight - 80, 30, () -> { view.page++; navigate(); }).active = view.page + 1 < pages;
        label("Page " + (view.page + 1) + " / " + pages, left + 40, top + panelHeight - 74, panelWidth - 80);
        button("Cancel", 12, top + panelHeight - 26, 80, this::cancel);
        undoButton = button("Undo", 100, top + panelHeight - 26, 56, () -> undo(false));
        undoButton.setTooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.undo.tooltip")));
        redoButton = button("Redo", 160, top + panelHeight - 26, 56, () -> undo(true));
        redoButton.setTooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.redo.tooltip")));
        applyButton = button("Apply", left + panelWidth - 80, top + panelHeight - 26, 80, this::applyDrafts);
        applyButton.setTooltip(Tooltip.create(Component.literal("Apply to the restriction draft. Use Save in the main editor to write changes to the server.")));
        refreshValidation();
    }

    private JsonArray sectionEntries() { return draft.getAsJsonArray(SECTIONS.get(selected).key()); }

    private void initSidebar() {
        label(SECTIONS.get(selected).label(), 16, top + 60, sidebarWidth - 12);
        entrySearch = edit(search, 16, top + 80, sidebarWidth - 12, "Filter entries by ID or text", text -> {
            search = text;
            sidebarScroll = 0;
            filterEntries();
        });
        entrySearch.setHint(Component.literal("Search entries...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        sidebarRows = Math.max(1, (height - 210) / SIDEBAR_ROW);
        filterEntries();
        button("+ New entry", 16, height - 92, sidebarWidth - 12, () -> {
            search = ""; sidebarScroll = 0;
            selectSection(selected);
            addEntry(views.peek());
            revealSidebar(sectionEntries().size() - 1);
        });
    }

    private void filterEntries() {
        sidebarEntries.clear();
        String filter = search.trim().toLowerCase(java.util.Locale.ROOT);
        JsonArray entries = sectionEntries();
        for (int i = 0; i < entries.size(); i++) {
            JsonElement value = entries.get(i);
            if (filter.isEmpty() || value.toString().toLowerCase(java.util.Locale.ROOT).contains(filter)) sidebarEntries.add(i);
        }
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, sidebarEntries.size() - sidebarRows));
    }

    private void revealSidebar(int index) {
        filterEntries();
        int row = sidebarEntries.indexOf(index);
        if (row >= 0) sidebarScroll = Math.max(0, Math.min(row, sidebarEntries.size() - sidebarRows));
    }

    private void openSidebarEntry(int index) {
        selectSection(selected);
        View root = views.peek();
        JsonElement value = root.value.getAsJsonArray().get(index);
        if (root.field.kind() == Kind.OBJECTS)
            views.push(new View(root.field, value, root.field.label() + " / " + (index + 1), childPath(root, index)));
        else root.page = index / rows;
        navigate();
        EditBox input = editFields.get("entry:" + index);
        if (input != null) { setFocused(input); input.setFocused(true); }
    }

    private void copyCurrentEntry(View view) {
        int index = (Integer) view.path.getLast();
        JsonArray owner = resolve(view.path.subList(0, view.path.size() - 1)).getAsJsonArray();
        JsonElement copy = view.value.deepCopy();
        owner.asList().add(index + 1, copy);
        changed(view);
        views.pop();
        View parentView = views.peek();
        views.push(new View(view.field, copy, view.field.label() + " / " + (index + 2), childPath(parentView, index + 1)));
        navigate();
        if (view.path.size() == 2) revealSidebar(index + 1);
    }

    private void removeCurrentEntry(View view) {
        int index = (Integer) view.path.getLast();
        JsonArray owner = resolve(view.path.subList(0, view.path.size() - 1)).getAsJsonArray();
        owner.remove(index);
        views.pop();
        changed(views.peek());
        navigate();
    }

    private int sidebarIndexAt(double x, double y) {
        if (x < 16 || x >= sidebarWidth + 4 || y < top + 106 || y >= top + 106 + sidebarRows * SIDEBAR_ROW) return -1;
        int row = sidebarScroll + (int) ((y - top - 106) / SIDEBAR_ROW);
        return row < sidebarEntries.size() ? sidebarEntries.get(row) : -1;
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int active = -1;
        View view = views.peek();
        if (view.path.size() > 1 && view.path.get(1) instanceof Integer index) active = index;
        int hovered = sidebarIndexAt(mouseX, mouseY);
        for (int row = 0; row < sidebarRows && sidebarScroll + row < sidebarEntries.size(); row++) {
            int index = sidebarEntries.get(sidebarScroll + row);
            int y = top + 106 + row * SIDEBAR_ROW;
            if (index == active || index == hovered) g.fill(16, y, sidebarWidth + 4, y + SIDEBAR_ROW - 1, index == active ? C_ROW_SELECTED : C_SLOT_HOVER);
            JsonElement entry = sectionEntries().get(index);
            String text = (index + 1) + ". " + (entry.isJsonObject() ? summary(entry.getAsJsonObject()) : entry.getAsString());
            g.text(font, font.plainSubstrByWidth(text, sidebarWidth - 22), 20, y + 6, index == active ? C_TEXT : C_MUTED, false);
            if (index == hovered) g.setComponentTooltipForNextFrame(font, List.of(Component.literal(text)), mouseX, mouseY);
        }
        if (sidebarEntries.isEmpty()) g.text(font, search.isBlank() ? "No entries yet" : "No matches", 20, top + 112, C_MUTED, false);
        if (sidebarEntries.size() > sidebarRows) {
            int track = sidebarRows * SIDEBAR_ROW;
            int thumb = Math.max(12, track * sidebarRows / sidebarEntries.size());
            int offset = (track - thumb) * sidebarScroll / (sidebarEntries.size() - sidebarRows);
            g.fill(sidebarWidth + 5, top + 106, sidebarWidth + 7, top + 106 + track, C_SLOT);
            g.fill(sidebarWidth + 5, top + 106 + offset, sidebarWidth + 7, top + 106 + offset + thumb, C_ACCENT);
        }
        g.text(font, sidebarEntries.size() + " / " + sectionEntries().size() + " entries", 16, height - 64, C_MUTED, false);
    }

    private void navigate() {
        history.endGroup();
        status = "";
        confirmDiscard = false;
        rebuildWidgets();
    }

    private void changed(View view) {
        changed(view, null);
    }

    private void changed(View view, String group) {
        attach(view);
        history.record(draft, group);
        status = "";
        confirmDiscard = false;
        refreshValidation();
        filterEntries();
    }

    private static List<Object> childPath(View view, Object key) {
        List<Object> path = new ArrayList<>(view.path); path.add(key); return List.copyOf(path);
    }

    private JsonElement resolve(List<Object> path) {
        JsonElement value = draft;
        for (Object key : path) {
            if (value == null) return null;
            if (key instanceof Integer index) {
                if (!value.isJsonArray() || index < 0 || index >= value.getAsJsonArray().size()) return null;
                value = value.getAsJsonArray().get(index);
            } else {
                if (!value.isJsonObject()) return null;
                value = value.getAsJsonObject().get((String) key);
            }
        }
        return value;
    }

    private void attach(View view) {
        var owner = resolve(view.path.subList(0, view.path.size() - 1));
        Object key = view.path.getLast();
        if (key instanceof Integer index) owner.getAsJsonArray().set(index, view.value);
        else owner.getAsJsonObject().add((String) key, view.value);
    }

    private void undo(boolean redo) {
        if (redo ? !history.canRedo() : !history.canUndo()) return;
        String focus = editFields.entrySet().stream().filter(entry -> entry.getValue() == getFocused()).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
        List<View> oldViews = new ArrayList<>(views);
        java.util.Collections.reverse(oldViews);
        draft = redo ? history.redo() : history.undo();
        views.clear();
        for (View old : oldViews) {
            JsonElement value = resolve(old.path);
            if (value == null) break;
            View restored = new View(old.field, value, old.title, old.path);
            restored.page = old.page; views.push(restored);
        }
        if (views.isEmpty()) selectSection(selected);
        confirmDiscard = false; status = "";
        rebuildWidgets();
        if (focus != null && editFields.containsKey(focus)) {
            var input = editFields.get(focus); setFocused(input); input.setFocused(true); input.moveCursorToEnd(false);
        }
    }

    private void marker(int x, int y, int w, EditBox input, String help, String placeholder, java.util.function.Supplier<String> check) {
        validation.add(new EditorValidation(x, y, w, 20, input, Component.literal(help), Component.literal(placeholder), check, true));
    }

    private String entryError(View view, int index) {
        var values = view.value.getAsJsonArray();
        if (index >= values.size()) return null;
        JsonArray single = new JsonArray(); single.add(values.get(index));
        try { RestrictionForm.validate(view.field, single, "Entry " + (index + 1)); return null; }
        catch (RuntimeException error) { return error.getMessage(); }
    }

    private String fieldError(View view, Field field) {
        JsonObject object = view.value.getAsJsonObject();
        try {
            RestrictionForm.validate(field, object.get(field.key()), field.label());
            if (view.field.key().equals("mobList")) {
                var filters = List.of("entityID", "entityModID", "entityTag", "mobCategory");
                if (filters.contains(field.key()) && filters.stream().noneMatch(object::has)) return "Choose at least one mob filter";
                if ((field.key().equals("minLight") || field.key().equals("maxLight")) && object.has("minLight") && object.has("maxLight")) {
                    for (Field peer : view.field.children()) if (peer.key().equals("minLight") || peer.key().equals("maxLight"))
                        RestrictionForm.validate(peer, object.get(peer.key()), peer.label());
                    if (object.get("minLight").getAsInt() > object.get("maxLight").getAsInt()) return "Minimum light exceeds maximum light";
                }
            }
            if (view.field.key().equals("recipeList") || view.field.key().equals("recipeExceptionList")) {
                if (List.of("recipe", "mod", "tag").stream().filter(object::has).count() != 1)
                    return "Choose exactly one recipe ID, mod ID, or output item tag";
            }
            if (view.field.key().equals("blockReplacementList")) {
                if ((field.key().equals("block") || field.key().equals("tag")) && object.has("block") == object.has("tag"))
                    return "Choose exactly one original block or tag";
                if ((field.key().equals("block") || field.key().equals("replacement")) && object.has(field.key())) {
                    var id = net.minecraft.resources.Identifier.tryParse(object.get(field.key()).getAsString());
                    if (id != null && !BuiltInRegistries.BLOCK.containsKey(id)) return "Unknown block: " + id;
                    if (id != null) {
                        var block = BuiltInRegistries.BLOCK.getValue(id);
                        boolean supported = field.key().equals("replacement")
                                ? com.decursioteam.decursio_stages.orestaging.OreStages.supported(block.defaultBlockState())
                                : block.getStateDefinition().getPossibleStates().stream().allMatch(com.decursioteam.decursio_stages.orestaging.OreStages::supportedSource);
                        if (!supported) return "Unsupported block for ore replacement";
                    }
                }
            }
            return null;
        } catch (RuntimeException error) { return error.getMessage(); }
    }

    private String draftError() {
        try {
            for (Field section : SECTIONS) {
                RestrictionForm.validate(section, draft.get(section.key()), section.label());
                if (section.key().equals("blockReplacementList")) {
                    var replacements = com.decursioteam.decursio_stages.restrictions.BlockReplacement.CODEC.listOf()
                            .parse(JsonOps.INSTANCE, draft.get(section.key())).getOrThrow();
                    replacements.forEach(com.decursioteam.decursio_stages.orestaging.OreStages::validate);
                }
            }
            return null;
        } catch (RuntimeException error) { return error.getMessage() == null ? "Invalid field value" : error.getMessage(); }
    }

    private void refreshValidation() {
        validation.forEach(EditorValidation::refresh);
        firstError = draftError();
        if (applyButton != null) applyButton.active = firstError == null;
        if (undoButton != null) undoButton.active = history.canUndo();
        if (redoButton != null) redoButton.active = history.canRedo();
    }

    private void addEntry(View view) {
        JsonArray values = view.value.getAsJsonArray();
        if (view.field.kind() == Kind.OBJECTS) {
            JsonObject object = newEntry(view.field);
            values.add(object);
            changed(view);
            views.push(new View(view.field, object, view.field.label() + " / " + values.size(), childPath(view, values.size() - 1)));
        } else {
            String value = "";
            if (view.field.kind() == Kind.CHOICES) {
                value = view.field.choices().stream().filter(choice -> !values.contains(new JsonPrimitive(choice))).findFirst().orElse(null);
                if (value == null) { status = "All choices are already included."; return; }
            }
            values.add(value);
            changed(view);
            view.page = (values.size() - 1) / rows;
        }
        navigate();
    }

    private void listRow(View view, int index, int y) {
        JsonArray values = view.value.getAsJsonArray();
        JsonElement value = values.get(index);
        int w = panelWidth - 120;
        if (view.field.kind() == Kind.OBJECTS || view.field.kind() == Kind.CHOICES)
            marker(left, y, w, null, view.field.hint(), "", () -> entryError(view, index));
        if (view.field.kind() == Kind.OBJECTS) {
            button((index + 1) + ". " + summary(value.getAsJsonObject()) + "  (Edit)", left, y, w, () -> {
                views.push(new View(view.field, value, view.field.label() + " / " + (index + 1), childPath(view, index)));
                navigate();
            });
        } else if (view.field.kind() == Kind.CHOICES) {
            button(value.getAsString(), left, y, w, () -> {
                List<String> choices = view.field.choices();
                int current = choices.indexOf(value.getAsString());
                for (int step = 1; step <= choices.size(); step++) {
                    String next = choices.get(Math.floorMod(current + step, choices.size()));
                    if (!values.contains(new JsonPrimitive(next))) {
                        values.set(index, new JsonPrimitive(next));
                        changed(view);
                        navigate();
                        break;
                    }
                }
            }).setTooltip(Tooltip.create(Component.literal("Click to change the spawn reason. Use + Add to include another.")));
        } else {
            EditBox input = editId(view.field.key(), view.field, value.getAsString(), left, y, w, text -> {
                values.set(index, new JsonPrimitive(text.trim()));
                changed(view, "entry:" + view.path + ":" + index);
            });
            editFields.put("entry:" + index, input);
            marker(left, y, w, input, view.field.hint(), "", () -> entryError(view, index));
        }
        button("Copy", left + panelWidth - 114, y, 50, () -> {
            values.asList().add(index + 1, values.get(index).deepCopy()); changed(view); navigate();
        }).setTooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.entry.copy.tooltip")));
        button("Remove", left + panelWidth - 60, y, 60, () -> {
            values.remove(index);
            changed(view);
            navigate();
        });
    }

    private void fieldRow(View view, Field field, int y) {
        JsonObject object = view.value.getAsJsonObject();
        JsonElement value = object.get(field.key());
        int labelWidth = Math.min(180, panelWidth / 2);
        int x = left + labelWidth;
        int w = panelWidth - labelWidth;
        label(field.label() + (field.required() ? " *" : ""), left, y + 6, labelWidth - 6);
        if (field.list() || field.kind() == Kind.BOOLEAN || field.kind() == Kind.CHOICE || field.kind() == Kind.COMPONENTS)
            marker(x, y, w, null, field.hint(), "", () -> fieldError(view, field));
        if (field.list()) {
            JsonArray array = value == null ? new JsonArray() : value.getAsJsonArray();
            boolean optionalOverride = !field.required() && view.field.key().equals("mobList");
            button("Edit entries (" + array.size() + ")", x, y, optionalOverride ? w - 66 : w, () -> {
                views.push(new View(field, array, field.label(), childPath(view, field.key())));
                navigate();
            }).setTooltip(Tooltip.create(Component.literal(field.hint())));
            if (optionalOverride) {
                Button unset = button("Unset", x + w - 62, y, 62, () -> {
                    object.remove(field.key());
                    changed(view);
                    navigate();
                });
                unset.active = value != null;
                unset.setTooltip(Tooltip.create(Component.literal("Remove this optional override entirely. An empty list can behave differently.")));
            }
        } else if (field.kind() == Kind.BOOLEAN) {
            boolean enabled = value != null && value.getAsBoolean();
            addRenderableWidget(new RestrictEditorScreen.SettingToggle(x, y, w, 20,
                    Component.literal("Enabled"), enabled, next -> {
                object.addProperty(field.key(), next);
                changed(view);
                navigate();
            })).setTooltip(Tooltip.create(Component.literal(field.hint())));
        } else if (field.kind() == Kind.CHOICE) {
            String current = value == null ? "" : value.getAsString();
            List<String> choices = new ArrayList<>(field.choices());
            if (!field.required()) choices.addFirst("");
            button(current.isEmpty() ? "Not set" : current, x, y, w, () -> {
                String next = choices.get((choices.indexOf(current) + 1) % choices.size());
                if (next.isEmpty()) object.remove(field.key());
                else object.addProperty(field.key(), next);
                changed(view);
                navigate();
            }).setTooltip(Tooltip.create(Component.literal(field.hint())));
        } else if (field.kind() == Kind.COMPONENTS) {
            button("Copy held item", x, y, w - 66, () -> copyHeldItem(view))
                    .setTooltip(Tooltip.create(Component.literal(field.hint())));
            button(value == null ? "None" : "Clear", x + w - 62, y, 62, () -> {
                object.remove(field.key());
                changed(view);
                navigate();
            }).active = value != null;
        } else {
            EditBox input = editId(view.field.key(), field, value == null ? "" : value.getAsString(), x, y, w, text -> {
                if (text.isBlank() && !field.required() && !field.key().equals("message")) object.remove(field.key());
                else if (field.kind() == Kind.NUMBER) {
                    try { object.addProperty(field.key(), Integer.parseInt(text)); }
                    catch (NumberFormatException error) { object.addProperty(field.key(), text); }
                } else object.addProperty(field.key(), field.kind() == Kind.TEXT ? text : text.trim());
                changed(view, "field:" + view.path + ":" + field.key());
            });
            input.setHint(Component.literal(defaultText(field)));
            editFields.put(field.key(), input);
            marker(x, y, w, input, field.hint(), defaultText(field), () -> fieldError(view, field));
        }
    }

    private void copyHeldItem(View view) {
        if (minecraft.player == null || minecraft.player.getMainHandItem().isEmpty()) {
            status = "Hold the equipment item in your main hand first.";
            return;
        }
        try {
            var stack = minecraft.player.getMainHandItem();
            JsonElement components = Dynamic.convert(NbtOps.INSTANCE, JsonOps.INSTANCE, ItemDataUtil.get(stack));
            JsonObject object = view.value.getAsJsonObject();
            object.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            object.add("nbt", components);
            changed(view);
            navigate();
            status = "Copied held item and its components. No items were moved.";
        } catch (RuntimeException error) {
            status = "Could not copy item components: " + error.getMessage();
        }
    }

    private String summary(JsonObject object) {
        if (object.has("recipe") && !object.get("recipe").getAsString().isBlank()) return object.get("recipe").getAsString();
        if (object.has("mod") && !object.get("mod").getAsString().isBlank()) return "@" + object.get("mod").getAsString();
        for (String key : List.of("block", "tag", "structure", "dimension", "entityID", "entityModID", "entityTag", "mobCategory", "effect", "item")) {
            if (object.has(key) && !object.get(key).getAsString().isBlank())
                return key.equals("tag") ? "#" + object.get(key).getAsString() : object.get(key).getAsString();
        }
        return "New entry";
    }

    private void applyDrafts() {
        JsonObject result = draft.deepCopy();
        try {
            for (int i = 0; i < SECTIONS.size(); i++) {
                Field section = SECTIONS.get(i);
                JsonElement value = result.get(section.key());
                RestrictionForm.validate(section, value, section.label());
                if (section.key().equals("recipeList") || section.key().equals("recipeExceptionList")) {
                    JsonArray array = value.getAsJsonArray();
                    for (int j = 0; j < array.size(); j++) {
                        if (array.get(j) instanceof JsonObject obj && obj.has("recipe") && obj.size() == 1) {
                            array.set(j, new JsonPrimitive(obj.get("recipe").getAsString()));
                        }
                    }
                }
                if (section.key().equals("structureList")) {
                    for (JsonElement entry : value.getAsJsonArray()) {
                        for (String action : List.of("use", "place", "break")) {
                            String key = "can_" + action + "_block_list";
                            if (!entry.getAsJsonObject().has(key)) entry.getAsJsonObject().add(key, new JsonArray());
                        }
                    }
                }
                CODECS.get(i).parse(JsonOps.INSTANCE, value).getOrThrow(IllegalArgumentException::new);
                if (section.key().equals("blockReplacementList")) {
                    var replacements = com.decursioteam.decursio_stages.restrictions.BlockReplacement.CODEC.listOf()
                            .parse(JsonOps.INSTANCE, value).getOrThrow();
                    replacements.forEach(com.decursioteam.decursio_stages.orestaging.OreStages::validate);
                }
            }
            if (!draft.equals(original)) apply.accept(result);
            minecraft.setScreen(parent);
        } catch (RuntimeException error) {
            status = error.getMessage() == null ? "Please check the fields." : error.getMessage();
        }
    }

    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20).build());
    }

    private List<SearchableIdBox.Option> options(String contextKey, String key) {
        return idOptions.computeIfAbsent(contextKey + ":" + key, ignored -> {
            List<SearchableIdBox.Option> result = new ArrayList<>();
            Consumer<String> id = value -> result.add(new SearchableIdBox.Option(value, value));
            var connection = minecraft.getConnection();
            switch (key) {
                case "recipe" -> parent.recipeIds().forEach(id);
                case "mod" -> {
                    java.util.Map<String, String> mods = new java.util.LinkedHashMap<>();
                    net.neoforged.fml.ModList.get().getMods().forEach(info -> mods.put(info.getModId(), info.getDisplayName()));
                    for (String recipeId : parent.recipeIds()) {
                        var parsed = net.minecraft.resources.Identifier.tryParse(recipeId);
                        if (parsed != null) mods.putIfAbsent(parsed.getNamespace(), parsed.getNamespace());
                    }
                    mods.forEach((modId, label) -> result.add(new SearchableIdBox.Option(modId, label)));
                }
                case "structure" -> parent.structureIds().forEach(id);
                case "dimension" -> {
                    if (connection != null) connection.levels().forEach(level -> id.accept(level.identifier().toString()));
                }
                case "entityID" -> BuiltInRegistries.ENTITY_TYPE.forEach(entity -> result.add(new SearchableIdBox.Option(
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString(), entity.getDescription().getString())));
                case "item" -> BuiltInRegistries.ITEM.forEach(item -> result.add(new SearchableIdBox.Option(
                        BuiltInRegistries.ITEM.getKey(item).toString(), item.getDefaultInstance().getHoverName().getString())));
                case "effect" -> BuiltInRegistries.MOB_EFFECT.forEach(effect -> result.add(new SearchableIdBox.Option(
                        BuiltInRegistries.MOB_EFFECT.getKey(effect).toString(), effect.getDisplayName().getString())));
                case "tag" -> {
                    if (contextKey.equals("recipeList") || contextKey.equals("recipeExceptionList"))
                        BuiltInRegistries.ITEM.listTags().forEach(tag -> id.accept(tag.key().location().toString()));
                    else BuiltInRegistries.BLOCK.listTags().forEach(tag -> id.accept(tag.key().location().toString()));
                }
                case "entityTag" -> BuiltInRegistries.ENTITY_TYPE.listTags().forEach(tag -> id.accept(tag.key().location().toString()));
                case "biomes" -> {
                    if (connection != null) connection.registryAccess().lookup(net.minecraft.core.registries.Registries.BIOME)
                            .ifPresent(registry -> registry.keySet().forEach(value -> id.accept(value.toString())));
                }
                case "block", "replacement", "can_use_block_list", "can_place_block_list", "can_break_block_list" ->
                        BuiltInRegistries.BLOCK.forEach(block -> result.add(new SearchableIdBox.Option(
                                BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString())));
                default -> { }
            }
            result.sort(java.util.Comparator.comparing(SearchableIdBox.Option::id));
            return List.copyOf(result);
        });
    }

    private EditBox editId(String contextKey, Field field, String value, int x, int y, int w, Consumer<String> responder) {
        if (field.kind() != Kind.ID && field.kind() != Kind.IDS && field.kind() != Kind.MOD)
            return edit(value, x, y, w, field.hint(), responder);
        SearchableIdBox box = new SearchableIdBox(font, x, y, w - 22, field.hint(), options(contextKey, field.key()));
        box.setMaxLength(Math.max(4096, value.length()));
        box.setValue(value);
        box.setTextColor(C_TEXT);
        box.setTextColorUneditable(C_MUTED);
        box.setTooltip(Tooltip.create(Component.literal("Type an ID or name to filter. Up/Down selects; Enter accepts; Escape closes.")));
        box.setResponder(text -> { responder.accept(text); box.filter(text); });
        addRenderableWidget(box);
        idBoxes.add(box);
        button("v", x + w - 20, y, 20, () -> {
            for (SearchableIdBox other : idBoxes) other.closePopup();
            setFocused(box);
            box.setFocused(true);
            box.browse();
        }).setTooltip(Tooltip.create(Component.literal("Browse all available IDs")));
        return box;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        for (SearchableIdBox box : idBoxes) {
            if (box.clickPopup(event.x(), event.y(), event.button(), height)) return true;
            box.closePopup();
        }
        int sidebarIndex = sidebarIndexAt(event.x(), event.y());
        if (event.button() == 0 && sidebarIndex >= 0) { openSidebarEntry(sidebarIndex); return true; }
        boolean handled = super.mouseClicked(event, doubleClick);
        // Screen focuses the clicked button after its callback, so restore focus for browse buttons.
        for (SearchableIdBox box : idBoxes) {
            if (event.button() == 0 && event.y() >= box.getY() && event.y() < box.getY() + box.getHeight()
                    && event.x() >= box.getX() && event.x() < box.getX() + box.getWidth() + 22) {
                setFocused(box);
                box.setFocused(true);
                if (event.x() >= box.getX() + box.getWidth()) box.browse();
                else box.filter(box.getValue());
                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        for (SearchableIdBox box : idBoxes) if (box.scrollPopup(x, y, vertical, height)) return true;
        if (x >= 12 && x <= sidebarWidth + 8 && y >= top + 104 && y < height - 96) {
            sidebarScroll = Math.max(0, Math.min(sidebarScroll + (vertical > 0 ? -1 : 1), sidebarEntries.size() - sidebarRows));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (minecraft.hasControlDown() && (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_Z || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_Y)) {
            undo(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_Y || minecraft.hasShiftDown()); return true;
        }
        if (getFocused() instanceof SearchableIdBox box && box.popupOpen()
                && (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
                || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER))
            return box.keyPressed(event);
        return super.keyPressed(event);
    }

    private EditBox edit(String value, int x, int y, int w, String hint, Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setMaxLength(Math.max(4096, value.length()));
        box.setValue(value);
        box.setTextColor(C_TEXT);
        box.setTextColorUneditable(C_MUTED);
        box.setResponder(responder);
        box.setTooltip(Tooltip.create(Component.literal(hint)));
        addRenderableWidget(box);
        return box;
    }

    private void label(String text, int x, int y, int w) { labels.add(new Label(text, x, y, w)); }

    private void cancel() {
        if (!draft.equals(original) && !confirmDiscard) {
            confirmDiscard = true;
            status = "Unsaved changes. Click Cancel again to discard all advanced edits.";
            return;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (views.size() > 1) { views.pop(); navigate(); }
        else cancel();
    }

    @Override
    public boolean isInGameUi() { return true; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, C_PANEL);
        g.fill(8, top + 24, width - 8, top + 51, C_SECTION);
        g.fill(10, top + 54, sidebarWidth + 10, height - 54, C_BORDER);
        g.fill(11, top + 55, sidebarWidth + 9, height - 55, C_SECTION);
        g.fill(left - 5, top + 54, left + panelWidth + 5, height - 96, C_BORDER);
        g.fill(left - 4, top + 55, left + panelWidth + 4, height - 97, C_SECTION);
        g.fill(8, height - 45, width - 8, height - 8, C_SECTION);
        int tabX = 12 + selected * ((width - 24) / SECTIONS.size());
        g.fill(tabX, top + 49, tabX + (width - 24) / SECTIONS.size() - 3, top + 51, C_ACCENT);
        g.text(font, title, 12, top + 8, C_ACCENT, false);
        renderSidebar(g, mouseX, mouseY);
        String state = draft.equals(original) ? "Advanced settings" : "Unsaved changes *";
        g.text(font, state, width - 12 - font.width(state), top + 8,
                draft.equals(original) ? C_MUTED : C_WARN, false);
        for (Label label : labels) g.text(font, font.plainSubstrByWidth(label.text(), label.width()), label.x(), label.y(), C_TEXT, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (var marker : validation) {
            marker.render(g, font);
            if (marker.error != null && mouseX >= marker.x && mouseX < marker.x + marker.width
                    && mouseY >= marker.y && mouseY < marker.y + marker.height + 11)
                g.setComponentTooltipForNextFrame(font, List.of(Component.literal(marker.error)), mouseX, mouseY);
        }
        String hint = switch (SECTIONS.get(selected).key()) {
            case "recipeList" -> "Recipes locked until the main stage is unlocked. Choose one recipe ID, mod ID, or output item tag.";
            case "recipeExceptionList" -> "Recipes exempt from this rule. Choose one recipe ID, mod ID, or output item tag.";
            case "structureList" -> "Structure rules apply while the player HAS the stage.";
            case "dimensionList" -> "Dimension access requires the stage. * Required field.";
            case "mobList" -> "Mob rules apply while the nearby player HAS the stage.";
            case "blockReplacementList" -> "Uses the main stage. Choose one original block OR tag; full cubes only.";
            default -> "Menu class names; these rules affect restricted items in menus.";
        };
        String message = !status.isEmpty() ? status : firstError != null ? firstError : hint;
        var lines = font.split(Component.literal(message), panelWidth);
        for (int i = 0; i < Math.min(3, lines.size()); i++)
            g.text(font, lines.get(i), left, top + panelHeight - 56 + i * 9, status.isEmpty() ? firstError == null ? C_MUTED : C_ERR : C_WARN);
        for (SearchableIdBox box : idBoxes) box.renderPopup(g, mouseX, mouseY, height);
    }
}
