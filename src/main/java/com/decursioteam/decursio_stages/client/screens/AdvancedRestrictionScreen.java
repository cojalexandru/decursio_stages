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
    private static final List<Codec<?>> CODECS = List.of(StructureRestriction.codec().listOf(),
            DimensionRestriction.codec().listOf(), MobRestriction.codec().listOf(), Codec.STRING.listOf(),
            com.decursioteam.decursio_stages.restrictions.BlockReplacement.CODEC.listOf());
    private static final class View {
        final Field field;
        final JsonElement value;
        final String title;
        final Runnable commit;
        int page;

        View(Field field, JsonElement value, String title, Runnable commit) {
            this.field = field;
            this.value = value;
            this.title = title;
            this.commit = commit;
        }
    }

    private record Label(String text, int x, int y, int width) {}
    private final RestrictEditorScreen parent;
    private final Consumer<JsonObject> apply;
    private final JsonObject draft = new JsonObject();
    private final JsonObject original;
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

    public AdvancedRestrictionScreen(RestrictEditorScreen parent, JsonObject data, Consumer<JsonObject> apply) {
        super(Component.literal("Advanced Restrictions"));
        this.parent = parent;
        this.apply = apply;
        for (Field section : SECTIONS) draft.add(section.key(), data.has(section.key())
                ? data.get(section.key()).deepCopy() : new JsonArray());
        original = draft.deepCopy();
        selectSection(0);
    }

    public RestrictEditorScreen parent() { return parent; }

    private void selectSection(int index) {
        selected = index;
        views.clear();
        Field section = SECTIONS.get(index);
        views.push(new View(section, draft.get(section.key()), section.label(), () -> {}));
    }

    @Override
    protected void init() {
        labels.clear();
        idBoxes.clear();
        panelWidth = Math.min(560, width - 32);
        panelHeight = Math.min(340, height - 16);
        top = (height - panelHeight) / 2;
        left = (width - panelWidth) / 2;
        rows = Math.max(1, (panelHeight - 174) / 28);
        for (int i = 0; i < SECTIONS.size(); i++) {
            final int tab = i;
            Field section = SECTIONS.get(i);
            Button button = button(section.label(), left + i * (panelWidth / SECTIONS.size()), top + 28, panelWidth / SECTIONS.size() - 3, () -> {
                selectSection(tab);
                navigate();
            });
            button.active = i != selected;
        }
        View view = views.peek();
        button("Back", left, top + 54, 60, () -> { views.pop(); navigate(); }).active = views.size() > 1;
        label(view.title, left + 68, top + 60, panelWidth - 158);
        boolean list = view.value.isJsonArray();
        if (list) button("+ Add", left + panelWidth - 80, top + 54, 80, () -> addEntry(view));
        int count = list ? view.value.getAsJsonArray().size() : view.field.children().size();
        int pages = Math.max(1, (count + rows - 1) / rows);
        view.page = Math.min(view.page, pages - 1);
        int first = view.page * rows;
        for (int i = first; i < Math.min(first + rows, count); i++) {
            int y = top + 84 + (i - first) * 28;
            if (list) listRow(view, i, y);
            else fieldRow(view, view.field.children().get(i), y);
        }
        if (count == 0) label("No entries. Click + Add to create one.", left, top + 90, panelWidth);
        button("<", left, top + panelHeight - 80, 30, () -> { view.page--; navigate(); }).active = view.page > 0;
        button(">", left + panelWidth - 30, top + panelHeight - 80, 30, () -> { view.page++; navigate(); }).active = view.page + 1 < pages;
        label("Page " + (view.page + 1) + " / " + pages, left + 40, top + panelHeight - 74, panelWidth - 80);
        button("Cancel", left, top + panelHeight - 26, 80, this::cancel);
        button("Apply", left + panelWidth - 80, top + panelHeight - 26, 80, this::applyDrafts);
    }

    private void navigate() {
        status = "";
        confirmDiscard = false;
        rebuildWidgets();
    }

    private void changed(View view) {
        view.commit.run();
        status = "";
        confirmDiscard = false;
    }

    private void addEntry(View view) {
        JsonArray values = view.value.getAsJsonArray();
        if (view.field.kind() == Kind.OBJECTS) {
            JsonObject object = newEntry(view.field);
            values.add(object);
            changed(view);
            views.push(new View(view.field, object, view.field.label() + " / " + values.size(), view.commit));
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
        int w = panelWidth - 64;
        if (view.field.kind() == Kind.OBJECTS) {
            button((index + 1) + ". " + summary(value.getAsJsonObject()) + "  (Edit)", left, y, w, () -> {
                views.push(new View(view.field, value, view.field.label() + " / " + (index + 1), view.commit));
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
            editId(view.field, value.getAsString(), left, y, w, text -> {
                values.set(index, new JsonPrimitive(text.trim()));
                changed(view);
            });
        }
        button("Remove", left + panelWidth - 60, y, 60, () -> {
            values.remove(index);
            changed(view);
            navigate();
        });
    }

    private void fieldRow(View view, Field field, int y) {
        JsonObject object = view.value.getAsJsonObject();
        JsonElement value = object.get(field.key());
        int labelWidth = Math.min(205, panelWidth / 2);
        int x = left + labelWidth;
        int w = panelWidth - labelWidth;
        label(field.label() + (field.required() ? " *" : ""), left, y + 6, labelWidth - 6);
        if (field.list()) {
            JsonArray array = value == null ? new JsonArray() : value.getAsJsonArray();
            boolean optionalOverride = !field.required() && view.field.key().equals("mobList");
            button("Edit entries (" + array.size() + ")", x, y, optionalOverride ? w - 66 : w, () -> {
                views.push(new View(field, array, field.label(), () -> {
                    // Opening an absent optional list does not create an override until it is edited.
                    object.add(field.key(), array);
                    view.commit.run();
                }));
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
            EditBox input = editId(field, value == null ? "" : value.getAsString(), x, y, w, text -> {
                if (text.isBlank() && !field.required() && !field.key().equals("message")) object.remove(field.key());
                else if (field.kind() == Kind.NUMBER) {
                    try { object.addProperty(field.key(), Integer.parseInt(text)); }
                    catch (NumberFormatException error) { object.addProperty(field.key(), text); }
                } else object.addProperty(field.key(), field.kind() == Kind.TEXT ? text : text.trim());
                changed(view);
            });
            input.setHint(Component.literal(defaultText(field)));
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
        for (String key : List.of("block", "tag", "structure", "dimension", "entityID", "entityModID", "entityTag", "mobCategory", "effect", "item")) {
            if (object.has(key) && !object.get(key).getAsString().isBlank()) return object.get(key).getAsString();
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

    private List<SearchableIdBox.Option> options(String key) {
        return idOptions.computeIfAbsent(key, ignored -> {
            List<SearchableIdBox.Option> result = new ArrayList<>();
            Consumer<String> id = value -> result.add(new SearchableIdBox.Option(value, value));
            var connection = minecraft.getConnection();
            switch (key) {
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
                case "tag" -> BuiltInRegistries.BLOCK.listTags().forEach(tag -> id.accept(tag.key().location().toString()));
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

    private EditBox editId(Field field, String value, int x, int y, int w, Consumer<String> responder) {
        if (field.kind() != Kind.ID && field.kind() != Kind.IDS)
            return edit(value, x, y, w, field.hint(), responder);
        SearchableIdBox box = new SearchableIdBox(font, x, y, w - 22, field.hint(), options(field.key()));
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
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
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
        g.fill(left - 7, top - 1, left + panelWidth + 7, top + panelHeight + 1, C_BORDER);
        g.fill(left - 6, top, left + panelWidth + 6, top + panelHeight, C_PANEL);
        g.fill(left - 2, top + 24, left + panelWidth + 2, top + 78, C_SECTION);
        g.fill(left - 2, top + 80, left + panelWidth + 2, top + panelHeight - 84, C_SECTION);
        g.fill(left - 2, top + panelHeight - 82, left + panelWidth + 2, top + panelHeight - 4, C_SECTION);
        int tabX = left + selected * (panelWidth / SECTIONS.size());
        g.fill(tabX, top + 49, tabX + panelWidth / SECTIONS.size() - 3, top + 51, C_ACCENT);
        g.text(font, title, left, top + 8, C_ACCENT, false);
        String state = draft.equals(original) ? "Advanced settings" : "Unsaved changes *";
        g.text(font, state, left + panelWidth - font.width(state), top + 8,
                draft.equals(original) ? C_MUTED : C_WARN, false);
        for (Label label : labels) g.text(font, font.plainSubstrByWidth(label.text(), label.width()), label.x(), label.y(), C_TEXT, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        String hint = switch (selected) {
            case 0 -> "Structure rules apply while the player HAS the stage.";
            case 1 -> "Dimension access requires the stage. * Required field.";
            case 2 -> "Mob rules apply while the nearby player HAS the stage.";
            case 4 -> "Uses the main stage. Choose one original block OR tag; full cubes only.";
            default -> "Menu class names; these rules affect restricted items in menus.";
        };
        String message = status.isEmpty() ? hint : status;
        var lines = font.split(Component.literal(message), panelWidth);
        for (int i = 0; i < Math.min(3, lines.size()); i++)
            g.text(font, lines.get(i), left, top + panelHeight - 56 + i * 9, status.isEmpty() ? C_MUTED : C_WARN);
        for (SearchableIdBox box : idBoxes) box.renderPopup(g, mouseX, mouseY, height);
    }
}
