package com.decursioteam.decursio_stages.client.screens;

import com.decursioteam.decursio_stages.utils.ItemDataUtil;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.network.RestrictionEditorServer;
import com.decursioteam.decursio_stages.network.messages.EditRestrictionMessage;
import com.decursioteam.decursio_stages.network.messages.OpenRestrictEditorMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;

import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.*;

/**
 * In-game editor for restriction files.
 *
 * <p>This uses an inert client menu for recipe viewer compatibility. Items are picked as "ghost" copies from the
 * player's inventory, so nothing is ever moved, lost or duplicated. All edits happen on a local copy of
 * the JSON and are sent to the server on save; the server validates, writes the file, reloads and replies
 * with a fresh snapshot.</p>
 */
@OnlyIn(Dist.CLIENT)
public class RestrictEditorScreen extends AbstractContainerScreen<RestrictionEditorMenu> {
    private static final int W = 420, H = 250;
    private static final int LIST_ROW = 12, LIST_ROWS = 15;
    private static final int GRID_COLS = 9, GRID_ROWS = 3, CELL = 18;
    private static final int MAX_PACKET = 32000;

    private static final int C_PANEL = 0xF0121218, C_BORDER = 0xFF3C3C52, C_SECTION = 0xFF1B1B24,
            C_SLOT = 0xFF2B2B38, C_SLOT_HOVER = 0xFF45455C, C_TEXT = 0xFFE6E6EE, C_MUTED = 0xFF9090A0,
            C_ACCENT = 0xFF7F6BFF, C_OK = 0xFF5CD67A, C_WARN = 0xFFE8C14A, C_ERR = 0xFFE85A5A;

    private record Setting(String key, boolean def) {}
    private static final List<Setting> SETTINGS = List.of(
            new Setting("hideInJEI_REI", true),
            new Setting("canPickupItems", false),
            new Setting("dropItemsFromInventory", true),
            new Setting("dropArmorFromInventory", true),
            new Setting("canUseItems", false),
            new Setting("canRightClickBlocks", false),
            new Setting("canBreakBlocks", false),
            new Setting("containerListWhitelist", false),
            new Setting("applyToFakePlayers", false));
    private static final List<String> TOOLTIP_MODES = List.of("ALWAYS", "ADVANCED", "NONE");
    private static final String DEFAULT_TITLE = "Locked Item";
    private static final int DEFAULT_DELAY = 60;

    // ---- data ----
    private Map<String, JsonObject> restrictions;
    private final List<String> filtered = new ArrayList<>();
    @Nullable private String pendingSelect;
    @Nullable private String selected;          // name as loaded from the server; null = new restriction
    private JsonObject working = new JsonObject(); // keeps fields this editor doesn't touch (mobList, dimensionList...)
    private final List<Entry> restricted = new ArrayList<>();
    private final List<Entry> exceptions = new ArrayList<>();
    private final boolean[] settingValues = new boolean[SETTINGS.size()];
    private String tooltipMode = "ALWAYS";
    private boolean showExceptions;
    private boolean dirty;
    private boolean loading;
    private boolean awaitingServer;
    private boolean initialized;

    // ---- ui ----
    private int left, top;
    private int listScroll, gridScroll;
    private EditBox searchBox, nameBox, stageBox, titleBox, delayBox, addBox;
    private final List<SettingToggle> toggles = new ArrayList<>();
    private Button tooltipButton, tabRestricted, tabExceptions, saveButton, deleteButton;
    @Nullable private String pendingConfirm;
    @Nullable private Component status;
    private int statusColor = C_MUTED;

    public RestrictEditorScreen(OpenRestrictEditorMessage message) {
        super(new RestrictionEditorMenu(), Minecraft.getInstance().player.getInventory(), Component.translatable("decursio_stages.gui.editor.title"));
        imageWidth = W;
        imageHeight = H;
        this.restrictions = message.getRestrictions();
        this.pendingSelect = message.getSelect();
        if (message.getStatus() != null) setStatus(message.getStatus(), message.isError() ? C_ERR : C_OK);
        for (int i = 0; i < SETTINGS.size(); i++) settingValues[i] = SETTINGS.get(i).def();
    }

    // =====================================================================================
    // Layout
    // =====================================================================================

    @Override
    protected void init() {
        super.init();
        left = Math.max(4, (width - W - 100) / 2);
        top = Math.max(4, (height - H - 14) / 2);
        leftPos = left;
        topPos = top;

        // Preserve text across resizes (init() is called again and widgets are recreated).
        String search = searchBox != null ? searchBox.getValue() : "";
        String name = nameBox != null ? nameBox.getValue() : "";
        String stage = stageBox != null ? stageBox.getValue() : "";
        String title = titleBox != null ? titleBox.getValue() : DEFAULT_TITLE;
        String delay = delayBox != null ? delayBox.getValue() : String.valueOf(DEFAULT_DELAY);
        String add = addBox != null ? addBox.getValue() : "";

        // Left column: search + list + new
        searchBox = addRenderableWidget(new EditBox(font, left + 6, top + 18, 110, 13, Component.translatable("decursio_stages.gui.editor.search")));
        searchBox.setHint(Component.translatable("decursio_stages.gui.editor.search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setTooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.search.tooltip")));
        searchBox.setValue(search);
        searchBox.setResponder(s -> { listScroll = 0; refilter(); });

        addRenderableWidget(Button.builder(Component.translatable("decursio_stages.gui.editor.new"), b -> requestNew())
                .bounds(left + 6, top + 228, 110, 16)
                .tooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.new.tooltip"))).build());

        // Centre column: fields
        int cx = left + 122;
        nameBox = addRenderableWidget(field(cx + 34, top + 18, 136, name, 64, "name"));
        nameBox.setFilter(s -> s.matches("[a-z0-9_\\-]*"));
        stageBox = addRenderableWidget(field(cx + 34, top + 36, 66, stage, 64, "stage"));
        delayBox = addRenderableWidget(field(cx + 134, top + 36, 36, delay, 6, "delay"));
        delayBox.setFilter(s -> s.matches("\\d*"));
        titleBox = addRenderableWidget(field(cx + 34, top + 54, 136, title, 64, "item_title"));

        tabRestricted = addRenderableWidget(Button.builder(Component.empty(), b -> setTab(false))
                .bounds(cx, top + 72, 84, 14)
                .tooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.tab.restricted.tooltip"))).build());
        tabExceptions = addRenderableWidget(Button.builder(Component.empty(), b -> setTab(true))
                .bounds(cx + 86, top + 72, 84, 14)
                .tooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.tab.exceptions.tooltip"))).build());

        addBox = addRenderableWidget(new EditBox(font, cx, top + 149, 144, 14, Component.translatable("decursio_stages.gui.editor.add")));
        addBox.setMaxLength(256);
        addBox.setHint(Component.translatable("decursio_stages.gui.editor.add.hint").withStyle(ChatFormatting.DARK_GRAY));
        addBox.setTooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.add.tooltip")));
        addBox.setValue(add);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> addFromField())
                .bounds(cx + 146, top + 149, 24, 14).build());

        // Right column: settings
        int rx = left + 298;
        toggles.clear();
        for (int i = 0; i < SETTINGS.size(); i++) {
            final int index = i;
            String key = SETTINGS.get(i).key();
            SettingToggle toggle = new SettingToggle(rx, top + 30 + i * 18, 116, 16,
                    Component.translatable("decursio_stages.gui.editor.setting." + key), settingValues[i],
                    v -> { settingValues[index] = v; markDirty(); });
            toggle.setTooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.setting." + key + ".tooltip")));
            toggles.add(addRenderableWidget(toggle));
        }
        tooltipButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleTooltipMode())
                .bounds(rx, top + 30 + SETTINGS.size() * 18 + 4, 116, 16)
                .tooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.setting.advancedTooltips.tooltip"))).build());

        deleteButton = addRenderableWidget(Button.builder(Component.empty(), b -> requestDelete())
                .bounds(rx, top + 228, 56, 16).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("decursio_stages.gui.editor.save"), b -> save())
                .bounds(rx + 60, top + 228, 56, 16)
                .tooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor.save.tooltip"))).build());

        refilter();
        if (!initialized) {
            initialized = true;
            if (pendingSelect != null && restrictions.containsKey(pendingSelect)) load(pendingSelect);
            else loadBlank();
            pendingSelect = null;
        }
        refreshLabels();
    }

    private EditBox field(int x, int y, int w, String value, int maxLength, String key) {
        EditBox box = new EditBox(font, x, y, w, 14, Component.translatable("decursio_stages.gui.editor." + key));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.translatable("decursio_stages.gui.editor." + key + ".tooltip")));
        box.setResponder(s -> markDirty());
        return box;
    }

    // =====================================================================================
    // Data <-> UI
    // =====================================================================================

    /** Called when the server answers (after a save/delete, or a new /decstages gui while open). */
    public void applyServerUpdate(OpenRestrictEditorMessage message) {
        awaitingServer = false;
        restrictions = message.getRestrictions();
        refilter();
        boolean error = message.isError();
        if (message.getStatus() != null) setStatus(message.getStatus(), error ? C_ERR : C_OK);
        if (!error) {
            // Success: show exactly what is now on disk.
            if (message.getSelect() != null && restrictions.containsKey(message.getSelect())) load(message.getSelect());
            else loadBlank();
        }
        // On error keep the user's unsaved edits so nothing is lost.
        refreshLabels();
    }

    private void load(String id) {
        JsonObject json = restrictions.get(id);
        if (json == null) return;
        loading = true;
        try {
            selected = id;
            working = json.deepCopy();
            JsonObject data = obj(working, "Restriction Data");
            JsonObject settings = obj(working, "Settings");

            nameBox.setValue(id);
            stageBox.setValue(str(data, "stage", ""));
            titleBox.setValue(str(settings, "itemsTitle", DEFAULT_TITLE));
            delayBox.setValue(String.valueOf(settings.has("itemsPickupDelay") ? settings.get("itemsPickupDelay").getAsInt() : DEFAULT_DELAY));
            for (int i = 0; i < SETTINGS.size(); i++) {
                Setting s = SETTINGS.get(i);
                settingValues[i] = settings.has(s.key()) ? settings.get(s.key()).getAsBoolean() : s.def();
                toggles.get(i).value = settingValues[i];
            }
            tooltipMode = str(settings, "advancedTooltips", "ALWAYS").toUpperCase(Locale.ROOT);
            if (!TOOLTIP_MODES.contains(tooltipMode)) tooltipMode = "ALWAYS";

            restricted.clear();
            exceptions.clear();
            arr(data, "itemList").forEach(e -> { if (e.isJsonObject()) addIfValid(restricted, Entry.fromObject(e.getAsJsonObject())); });
            arr(data, "tagList").forEach(e -> restricted.add(Entry.of(Kind.TAG, e.getAsString(), null)));
            arr(data, "modList").forEach(e -> restricted.add(Entry.of(Kind.MOD, e.getAsString(), null)));
            arr(data, "fluidList").forEach(e -> { if (e.isJsonObject()) addIfValid(restricted, Entry.fromFluidObject(e.getAsJsonObject())); });
            arr(data, "exceptionList").forEach(e -> { if (e.isJsonObject()) addIfValid(exceptions, Entry.fromObject(e.getAsJsonObject())); });
        } catch (Exception e) {
            DecursioStages.LOGGER.error("Restriction editor: failed to read restriction {}", id, e);
            setStatus(Component.translatable("decursio_stages.gui.editor.status.load_failed", id), C_ERR);
        } finally {
            loading = false;
        }
        gridScroll = 0;
        dirty = false;
        pendingConfirm = null;
        refreshLabels();
    }

    private void loadBlank() {
        loading = true;
        selected = null;
        working = new JsonObject();
        nameBox.setValue("");
        stageBox.setValue("");
        titleBox.setValue(DEFAULT_TITLE);
        delayBox.setValue(String.valueOf(DEFAULT_DELAY));
        for (int i = 0; i < SETTINGS.size(); i++) {
            settingValues[i] = SETTINGS.get(i).def();
            toggles.get(i).value = settingValues[i];
        }
        tooltipMode = "ALWAYS";
        restricted.clear();
        exceptions.clear();
        loading = false;
        gridScroll = 0;
        dirty = false;
        pendingConfirm = null;
        refreshLabels();
    }

    private JsonObject buildJson() {
        JsonObject json = working.deepCopy();
        JsonObject data = obj(json, "Restriction Data");
        JsonObject settings = obj(json, "Settings");
        json.add("Restriction Data", data);
        json.add("Settings", settings);

        data.remove("name");
        data.addProperty("stage", stageBox.getValue().trim());
        JsonArray items = new JsonArray(), tags = new JsonArray(), mods = new JsonArray(), excl = new JsonArray(), fluids = new JsonArray();
        for (Entry e : restricted) {
            switch (e.kind) {
                case ITEM -> items.add(e.toObject());
                case TAG -> tags.add(e.id);
                case MOD -> mods.add(e.id);
                case FLUID, FLUID_TAG, FLUID_MOD -> fluids.add(e.toFluidObject());
            }
        }
        exceptions.forEach(e -> excl.add(e.toObject()));
        data.add("fluidList", fluids);
        data.add("itemList", items);
        data.add("tagList", tags);
        data.add("modList", mods);
        data.add("exceptionList", excl);

        settings.addProperty("advancedTooltips", tooltipMode);
        settings.addProperty("itemsTitle", titleBox.getValue());
        settings.addProperty("itemsPickupDelay", parseDelay());
        for (int i = 0; i < SETTINGS.size(); i++) settings.addProperty(SETTINGS.get(i).key(), settingValues[i]);
        return json;
    }

    private int parseDelay() {
        try {
            return Math.max(0, Integer.parseInt(delayBox.getValue()));
        } catch (NumberFormatException e) {
            return DEFAULT_DELAY;
        }
    }

    // =====================================================================================
    // Actions
    // =====================================================================================

    private void select(String id) {
        if (id.equals(selected)) return;
        if (dirty && !confirm("switch:" + id, Component.translatable("decursio_stages.gui.editor.confirm.discard"))) return;
        load(id);
    }

    private void requestNew() {
        if (dirty && !confirm("new", Component.translatable("decursio_stages.gui.editor.confirm.discard"))) return;
        loadBlank();
        setFocused(nameBox);
        setStatus(Component.translatable("decursio_stages.gui.editor.status.new"), C_MUTED);
    }

    private void requestDelete() {
        if (selected == null) {
            setStatus(Component.translatable("decursio_stages.gui.editor.status.nothing_to_delete"), C_WARN);
            return;
        }
        if (!confirm("delete", Component.translatable("decursio_stages.gui.editor.confirm.delete", selected))) return;
        send(new EditRestrictionMessage(EditRestrictionMessage.Action.DELETE, selected, selected, "{}"));
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (!RestrictionEditorServer.VALID_NAME.matcher(name).matches()) {
            setStatus(Component.translatable("decursio_stages.gui.editor.status.invalid_name"), C_ERR);
            setFocused(nameBox);
            return;
        }
        if (stageBox.getValue().isBlank()) {
            setStatus(Component.translatable("decursio_stages.gui.editor.status.no_stage"), C_ERR);
            setFocused(stageBox);
            return;
        }
        if (!name.equals(selected) && restrictions.containsKey(name)) {
            setStatus(Component.translatable("decursio_stages.gui.editor.status.exists", name), C_ERR);
            return;
        }
        send(new EditRestrictionMessage(EditRestrictionMessage.Action.SAVE, name, selected == null ? "" : selected, buildJson().toString()));
    }

    private void send(EditRestrictionMessage message) {
        if (awaitingServer) return;
        if (message.estimatedSize() > MAX_PACKET) {
            setStatus(Component.translatable("decursio_stages.gui.editor.status.too_large"), C_ERR);
            return;
        }
        awaitingServer = true;
        pendingConfirm = null;
        setStatus(Component.translatable("decursio_stages.gui.editor.status.saving"), C_MUTED);
        DecursioStages.NETWORK.sendToServer(message);
    }

    private void setTab(boolean exceptionsTab) {
        showExceptions = exceptionsTab;
        gridScroll = 0;
        refreshLabels();
    }

    private void cycleTooltipMode() {
        tooltipMode = TOOLTIP_MODES.get((TOOLTIP_MODES.indexOf(tooltipMode) + 1) % TOOLTIP_MODES.size());
        markDirty();
        refreshLabels();
    }

    public Rect2i editorBounds() {
        return new Rect2i(left, top, W, H);
    }

    public Rect2i dropBounds() {
        return new Rect2i(left + 122, top + 90, GRID_COLS * CELL, GRID_ROWS * CELL);
    }

    public boolean acceptsDrop(double x, double y) {
        return !awaitingServer && dropBounds().contains((int) x, (int) y);
    }

    public boolean acceptItem(ItemStack stack) {
        if (awaitingServer || stack.isEmpty()) return false;
        addFromInventory(stack, hasShiftDown());
        return true;
    }

    public boolean acceptFluid(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (awaitingServer || id == null || fluid == Fluids.EMPTY) return false;
        addEntry(Entry.of(Kind.FLUID, id.toString(), null));
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        setDragging(false);
        return getFocused() != null && getFocused().mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return isDragging() && getFocused() != null && getFocused().mouseDragged(x, y, button, dx, dy);
    }

    private void addFromInventory(ItemStack stack, boolean withNbt) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        JsonElement nbt = null;
        if (withNbt && ItemDataUtil.has(stack)) nbt = Dynamic.convert(NbtOps.INSTANCE, JsonOps.INSTANCE, ItemDataUtil.get(stack));
        addEntry(Entry.of(Kind.ITEM, id.toString(), nbt));
    }

    private void addFromField() {
        String raw = addBox.getValue().trim();
        if (raw.isEmpty()) return;
        Entry entry;
        if (raw.startsWith("fluid:") || raw.startsWith("fluidTag:") || raw.startsWith("fluidMod:")) {
            int colon = raw.indexOf(':');
            String id = raw.substring(colon + 1);
            Kind kind = raw.startsWith("fluidTag:") ? Kind.FLUID_TAG : raw.startsWith("fluidMod:") ? Kind.FLUID_MOD : Kind.FLUID;
            if (kind == Kind.FLUID_MOD ? !ModList.get().isLoaded(id) : ResourceLocation.tryParse(id) == null) {
                setStatus(Component.translatable("decursio_stages.gui.editor.status.bad_id", raw), C_ERR);
                return;
            }
            if (kind == Kind.FLUID && !BuiltInRegistries.FLUID.containsKey(ResourceLocation.tryParse(id))) {
                setStatus(Component.translatable("decursio_stages.gui.editor.status.bad_id", raw), C_ERR);
                return;
            }
            entry = Entry.of(kind, id, null);
        } else if (raw.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(raw.substring(1));
            if (rl == null) { setStatus(Component.translatable("decursio_stages.gui.editor.status.bad_id", raw), C_ERR); return; }
            var tags = BuiltInRegistries.ITEM;
            if (tags != null && tags.getTag(ItemTags.create(rl)).isEmpty())
                setStatus(Component.translatable("decursio_stages.gui.editor.status.unknown_tag", rl.toString()), C_WARN);
            entry = Entry.of(Kind.TAG, rl.toString(), null);
        } else if (raw.startsWith("@")) {
            String mod = raw.substring(1).toLowerCase(Locale.ROOT);
            if (!ModList.get().isLoaded(mod)) { setStatus(Component.translatable("decursio_stages.gui.editor.status.unknown_mod", mod), C_ERR); return; }
            entry = Entry.of(Kind.MOD, mod, null);
        } else {
            ResourceLocation rl = ResourceLocation.tryParse(raw);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                setStatus(Component.translatable("decursio_stages.gui.editor.status.unknown_item", raw), C_ERR);
                return;
            }
            entry = Entry.of(Kind.ITEM, rl.toString(), null);
        }
        if (addEntry(entry)) addBox.setValue("");
    }

    private boolean addEntry(Entry entry) {
        List<Entry> target = currentList();
        if (target.stream().anyMatch(entry::sameAs)) {
            setStatus(Component.translatable("decursio_stages.gui.editor.status.duplicate"), C_WARN);
            return false;
        }
        target.add(entry);
        int rows = (target.size() + GRID_COLS - 1) / GRID_COLS;
        gridScroll = Math.max(0, rows - GRID_ROWS); // jump to show the new entry
        markDirty();
        refreshLabels();
        return true;
    }

    private void removeEntry(int index) {
        List<Entry> list = currentList();
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        gridScroll = Math.min(gridScroll, maxGridScroll());
        markDirty();
        refreshLabels();
    }

    private List<Entry> currentList() {
        return showExceptions ? exceptions : restricted;
    }

    private void markDirty() {
        if (loading) return;
        dirty = true;
        pendingConfirm = null;
    }

    /** Two-click confirmation for destructive actions. Returns true on the second click. */
    private boolean confirm(String action, Component warning) {
        if (action.equals(pendingConfirm)) {
            pendingConfirm = null;
            return true;
        }
        pendingConfirm = action;
        setStatus(warning, C_WARN);
        refreshLabels();
        return false;
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    private void refilter() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        restrictions.forEach((id, json) -> {
            if (query.isEmpty() || id.contains(query) || str(obj(json, "Restriction Data"), "stage", "").toLowerCase(Locale.ROOT).contains(query))
                filtered.add(id);
        });
        listScroll = Math.min(listScroll, Math.max(0, filtered.size() - LIST_ROWS));
    }

    private void refreshLabels() {
        if (tabRestricted == null) return;
        tabRestricted.setMessage(Component.translatable("decursio_stages.gui.editor.tab.restricted", restricted.size()));
        tabExceptions.setMessage(Component.translatable("decursio_stages.gui.editor.tab.exceptions", exceptions.size()));
        tabRestricted.active = showExceptions;
        tabExceptions.active = !showExceptions;
        tooltipButton.setMessage(Component.translatable("decursio_stages.gui.editor.setting.advancedTooltips",
                Component.translatable("decursio_stages.gui.editor.tooltip_mode." + tooltipMode.toLowerCase(Locale.ROOT))));
        deleteButton.active = selected != null;
        deleteButton.setMessage("delete".equals(pendingConfirm)
                ? Component.translatable("decursio_stages.gui.editor.delete.confirm").withStyle(ChatFormatting.RED)
                : Component.translatable("decursio_stages.gui.editor.delete"));
    }

    // =====================================================================================
    // Input
    // =====================================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Dispatch only to widgets: vanilla container clicks must never reach the server.
        for (var child : children()) {
            if (child.mouseClicked(mx, my, button)) { setFocused(child); setDragging(button == 0); return true; }
        }

        // Restriction list
        int listIndex = listIndexAt(mx, my);
        if (listIndex >= 0) {
            select(filtered.get(listIndex));
            setFocused(null);
            return true;
        }
        // Entry grid: click removes
        int gridIndex = gridIndexAt(mx, my);
        if (gridIndex >= 0 && gridIndex < currentList().size()) {
            removeEntry(gridIndex);
            setFocused(null);
            return true;
        }
        // Inventory: click adds a ghost copy
        int slot = inventorySlotAt(mx, my);
        if (slot >= 0) {
            ItemStack stack = minecraft.player.getInventory().items.get(slot);
            if (!stack.isEmpty()) addFromInventory(stack, hasShiftDown());
            setFocused(null);
            return true;
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double delta) {
        int step = delta > 0 ? -1 : 1;
        if (inside(mx, my, left + 6, top + 34, 110, LIST_ROWS * LIST_ROW)) {
            listScroll = clamp(listScroll + step, 0, Math.max(0, filtered.size() - LIST_ROWS));
            return true;
        }
        if (inside(mx, my, left + 122, top + 90, GRID_COLS * CELL + 8, GRID_ROWS * CELL)) {
            gridScroll = clamp(gridScroll + step, 0, maxGridScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_S && hasControlDown()) { save(); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && getFocused() == addBox) { addFromField(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return false;
    }

    @Override
    public void onClose() {
        if (dirty && !confirm("close", Component.translatable("decursio_stages.gui.editor.confirm.close"))) return;
        minecraft.setScreen(null);
    }

    @Override
    protected void containerTick() {

        saveButton.active = !awaitingServer;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    // Rendering
    // =====================================================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);

        // Panel + sections
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, C_BORDER);
        g.fill(left, top, left + W, top + H, C_PANEL);
        g.fill(left + 4, top + 15, left + 118, top + H - 4, C_SECTION);
        g.fill(left + 120, top + 15, left + 294, top + H - 4, C_SECTION);
        g.fill(left + 296, top + 15, left + W - 4, top + H - 4, C_SECTION);

        g.drawString(font, title, left + 6, top + 4, C_ACCENT, false);
        String current = (selected == null ? Component.translatable("decursio_stages.gui.editor.unsaved_new").getString() : selected) + (dirty ? " *" : "");
        g.drawString(font, current, left + W - 6 - font.width(current), top + 4, dirty ? C_WARN : C_MUTED, false);

        int cx = left + 122;
        g.drawString(font, Component.translatable("decursio_stages.gui.editor.name"), cx, top + 21, C_TEXT, false);
        g.drawString(font, Component.translatable("decursio_stages.gui.editor.stage"), cx, top + 39, C_TEXT, false);
        g.drawString(font, Component.translatable("decursio_stages.gui.editor.delay"), cx + 104, top + 39, C_TEXT, false);
        g.drawString(font, Component.translatable("decursio_stages.gui.editor.item_title"), cx, top + 57, C_TEXT, false);
        g.drawString(font, Component.translatable("decursio_stages.gui.editor.settings"), left + 298, top + 19, C_TEXT, false);

        renderList(g, mx, my);
        renderGrid(g, mx, my);
        renderInventory(g, mx, my);

        super.render(g, mx, my, partialTick);

        if (status != null) {
            g.drawCenteredString(font, status, left + W / 2, top + H + 4, statusColor);
        }
        renderHoverTooltips(g, mx, my);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int x = left + 6, y = top + 34, w = 110;
        if (filtered.isEmpty()) {
            g.drawString(font, Component.translatable(restrictions.isEmpty() ? "decursio_stages.gui.editor.list.empty" : "decursio_stages.gui.editor.list.no_match"), x + 2, y + 2, C_MUTED, false);
            return;
        }
        int hovered = listIndexAt(mx, my);
        for (int row = 0; row < LIST_ROWS && listScroll + row < filtered.size(); row++) {
            int index = listScroll + row;
            String id = filtered.get(index);
            int ry = y + row * LIST_ROW;
            boolean isSelected = id.equals(selected);
            if (isSelected) g.fill(x, ry, x + w - 4, ry + LIST_ROW, 0x557F6BFF);
            else if (index == hovered) g.fill(x, ry, x + w - 4, ry + LIST_ROW, 0x30FFFFFF);
            String label = font.plainSubstrByWidth(id + (isSelected && dirty ? " *" : ""), w - 8);
            g.drawString(font, label, x + 2, ry + 2, isSelected ? 0xFFFFFFFF : C_TEXT, false);
        }
        drawScrollbar(g, x + w - 3, y, LIST_ROWS * LIST_ROW, listScroll, filtered.size(), LIST_ROWS);
    }

    private void renderGrid(GuiGraphics g, int mx, int my) {
        int x = left + 122, y = top + 90;
        List<Entry> list = currentList();
        int hovered = gridIndexAt(mx, my);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = (gridScroll + row) * GRID_COLS + col;
                int sx = x + col * CELL, sy = y + row * CELL;
                boolean has = index < list.size();
                g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, has && index == hovered ? 0xFF6A3A3A : C_SLOT);
                if (has) {
                    Entry e = list.get(index);
                    g.renderItem(e.icon, sx + 1, sy + 1);
                    if (e.kind != Kind.ITEM || e.nbt != null) {
                        String badge = switch (e.kind) {
                            case ITEM -> "N";
                            case TAG -> "#";
                            case MOD -> "@";
                            case FLUID -> "F";
                            case FLUID_TAG -> "F#";
                            case FLUID_MOD -> "F@";
                        };
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        g.drawString(font, badge, sx + CELL - 1 - font.width(badge), sy + CELL - 9, e.kind == Kind.ITEM ? C_WARN : C_ACCENT, true);
                        g.pose().popPose();
                    }
                }
            }
        }
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("decursio_stages.gui.editor.grid.empty"), x + GRID_COLS * CELL / 2, y + CELL + 5, C_MUTED);
        }
        int rows = (list.size() + GRID_COLS - 1) / GRID_COLS;
        drawScrollbar(g, x + GRID_COLS * CELL + 2, y, GRID_ROWS * CELL - 1, gridScroll, rows, GRID_ROWS);
    }

    private void renderInventory(GuiGraphics g, int mx, int my) {
        List<ItemStack> items = minecraft.player.getInventory().items;
        int hovered = inventorySlotAt(mx, my);
        for (int slot = 0; slot < 36; slot++) {
            int[] pos = inventorySlotPos(slot);
            g.fill(pos[0], pos[1], pos[0] + CELL - 1, pos[1] + CELL - 1, slot == hovered ? C_SLOT_HOVER : C_SLOT);
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                g.renderItem(stack, pos[0] + 1, pos[1] + 1);
                g.renderItemDecorations(font, stack, pos[0] + 1, pos[1] + 1);
            }
        }
    }

    private void renderHoverTooltips(GuiGraphics g, int mx, int my) {
        int gridIndex = gridIndexAt(mx, my);
        if (gridIndex >= 0 && gridIndex < currentList().size()) {
            Entry e = currentList().get(gridIndex);
            List<Component> lines = new ArrayList<>();
            switch (e.kind) {
                case ITEM -> {
                    lines.add(e.icon.getHoverName());
                    lines.add(Component.literal(e.id).withStyle(ChatFormatting.DARK_GRAY));
                    if (e.missing) lines.add(Component.translatable("decursio_stages.gui.editor.entry.missing").withStyle(ChatFormatting.RED));
                    if (e.nbt != null) {
                        lines.add(Component.translatable("decursio_stages.gui.editor.entry.nbt").withStyle(ChatFormatting.GOLD));
                        lines.add(Component.literal(font.plainSubstrByWidth(e.nbt.toString(), 220)).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                case TAG -> {
                    lines.add(Component.translatable("decursio_stages.gui.editor.entry.tag", e.id).withStyle(ChatFormatting.AQUA));
                    lines.add(Component.translatable("decursio_stages.gui.editor.entry.tag.count", tagSize(e.id)).withStyle(ChatFormatting.GRAY));
                }
                case FLUID, FLUID_TAG, FLUID_MOD -> lines.add(Component.literal(
                        (e.kind == Kind.FLUID ? "Fluid: " : e.kind == Kind.FLUID_TAG ? "Fluid tag: #" : "Fluids from mod: ") + e.id).withStyle(ChatFormatting.AQUA));
                case MOD -> lines.add(Component.translatable("decursio_stages.gui.editor.entry.mod", modName(e.id)).withStyle(ChatFormatting.AQUA));
            }
            lines.add(Component.translatable("decursio_stages.gui.editor.entry.remove").withStyle(ChatFormatting.RED));
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }

        int slot = inventorySlotAt(mx, my);
        if (slot >= 0) {
            ItemStack stack = minecraft.player.getInventory().items.get(slot);
            if (stack.isEmpty()) return;
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, stack));
            lines.add(Component.translatable(showExceptions ? "decursio_stages.gui.editor.inventory.add_exception" : "decursio_stages.gui.editor.inventory.add").withStyle(ChatFormatting.GREEN));
            if (ItemDataUtil.has(stack)) lines.add(Component.translatable("decursio_stages.gui.editor.inventory.add_nbt").withStyle(ChatFormatting.GOLD));
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }

        int listIndex = listIndexAt(mx, my);
        if (listIndex >= 0) {
            JsonObject data = obj(restrictions.get(filtered.get(listIndex)), "Restriction Data");
            List<Component> lines = List.of(
                    Component.literal(filtered.get(listIndex)).withStyle(ChatFormatting.WHITE),
                    Component.translatable("decursio_stages.gui.editor.list.stage", str(data, "stage", "?")).withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("decursio_stages.gui.editor.list.counts",
                            arr(data, "itemList").size(), arr(data, "tagList").size(), arr(data, "modList").size(), arr(data, "exceptionList").size())
                            .withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int total, int visible) {
        if (total <= visible) return;
        g.fill(x, y, x + 2, y + h, 0xFF2A2A33);
        int thumb = Math.max(8, h * visible / total);
        int ty = y + (h - thumb) * scroll / Math.max(1, total - visible);
        g.fill(x, ty, x + 2, ty + thumb, C_ACCENT);
    }

    // =====================================================================================
    // Hit testing
    // =====================================================================================

    private int listIndexAt(double mx, double my) {
        if (!inside(mx, my, left + 6, top + 34, 106, LIST_ROWS * LIST_ROW)) return -1;
        int index = listScroll + (int) ((my - (top + 34)) / LIST_ROW);
        return index < filtered.size() ? index : -1;
    }

    private int gridIndexAt(double mx, double my) {
        int x = left + 122, y = top + 90;
        if (!inside(mx, my, x, y, GRID_COLS * CELL, GRID_ROWS * CELL)) return -1;
        int col = (int) ((mx - x) / CELL), row = (int) ((my - y) / CELL);
        return (gridScroll + row) * GRID_COLS + col;
    }

    /** Slots 9-35 are the main inventory, 0-8 the hotbar (drawn below, like vanilla). */
    private int[] inventorySlotPos(int slot) {
        int x = left + 122, y = top + 167;
        if (slot < 9) return new int[]{x + slot * CELL, y + 3 * CELL + 4};
        int i = slot - 9;
        return new int[]{x + (i % 9) * CELL, y + (i / 9) * CELL};
    }

    private int inventorySlotAt(double mx, double my) {
        for (int slot = 0; slot < 36; slot++) {
            int[] p = inventorySlotPos(slot);
            if (inside(mx, my, p[0], p[1], CELL - 1, CELL - 1)) return slot;
        }
        return -1;
    }

    private int maxGridScroll() {
        int rows = (currentList().size() + GRID_COLS - 1) / GRID_COLS;
        return Math.max(0, rows - GRID_ROWS);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // =====================================================================================
    // JSON helpers
    // =====================================================================================

    private static JsonObject obj(@Nullable JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray arr(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject parent, String key, String def) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsString() : def;
    }

    private static void addIfValid(List<Entry> list, @Nullable Entry entry) {
        if (entry != null) list.add(entry);
    }

    private static int tagSize(String id) {
        var tags = BuiltInRegistries.ITEM;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return tags == null || rl == null ? 0 : tags.getTag(ItemTags.create(rl)).map(net.minecraft.core.HolderSet::size).orElse(0);
    }

    private static String modName(String id) {
        return ModList.get().getModContainerById(id).map(c -> c.getModInfo().getDisplayName()).orElse(id);
    }

    // =====================================================================================
    // Entries
    // =====================================================================================

    private enum Kind { ITEM, TAG, MOD, FLUID, FLUID_TAG, FLUID_MOD }

    private static final class Entry {
        final Kind kind;
        final String id;
        @Nullable final JsonElement nbt;
        final ItemStack icon;
        final boolean missing;

        private Entry(Kind kind, String id, @Nullable JsonElement nbt, ItemStack icon, boolean missing) {
            this.kind = kind;
            this.id = id;
            this.nbt = nbt;
            this.icon = icon;
            this.missing = missing;
        }

        static Entry of(Kind kind, String id, @Nullable JsonElement nbt) {
            ItemStack icon = ItemStack.EMPTY;
            boolean missing = false;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            switch (kind) {
                case ITEM -> {
                    Item item = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
                    if (item == null || item == Items.AIR) {
                        missing = true;
                        icon = new ItemStack(Items.BARRIER);
                    } else {
                        icon = new ItemStack(item);
                        if (nbt != null) {
                            Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, nbt);
                            if (tag instanceof CompoundTag compound) ItemDataUtil.applyPreview(icon, compound);
                        }
                    }
                }
                case FLUID -> {
                    var fluid = rl == null ? null : BuiltInRegistries.FLUID.get(rl);
                    missing = fluid == null;
                    icon = fluid == null ? new ItemStack(Items.BARRIER) : new ItemStack(fluid.getBucket());
                    if (icon.isEmpty()) icon = new ItemStack(Items.BUCKET);
                }
                case FLUID_TAG, FLUID_MOD -> icon = new ItemStack(Items.BUCKET);
                case TAG -> {
                    var tags = BuiltInRegistries.ITEM;
                    if (tags != null && rl != null) {
                        TagKey<Item> key = ItemTags.create(rl);
                        icon = tags.getTag(key).stream().flatMap(net.minecraft.core.HolderSet::stream).findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
                    }
                    if (icon.isEmpty()) icon = new ItemStack(Items.NAME_TAG);
                }
                case MOD -> {
                    icon = BuiltInRegistries.ITEM.entrySet().stream()
                            .filter(e -> e.getKey().location().getNamespace().equals(id))
                            .findFirst().map(e -> new ItemStack(e.getValue())).orElse(new ItemStack(Items.BOOK));
                }
            }
            return new Entry(kind, id, nbt, icon, missing);
        }

        /** Reads an itemList / exceptionList object: {"item": ..., "nbt": ...} or {"tag": ...} or {"mod": ...}. */
        @Nullable
        static Entry fromObject(JsonObject json) {
            if (json.has("fluid")) return of(Kind.FLUID, json.get("fluid").getAsString(), null);
            if (json.has("fluidTag")) return of(Kind.FLUID_TAG, json.get("fluidTag").getAsString(), null);
            if (json.has("fluidMod")) return of(Kind.FLUID_MOD, json.get("fluidMod").getAsString(), null);
            if (json.has("item")) return of(Kind.ITEM, json.get("item").getAsString(), json.has("nbt") ? json.get("nbt") : null);
            if (json.has("tag")) return of(Kind.TAG, json.get("tag").getAsString(), null);
            if (json.has("mod")) return of(Kind.MOD, json.get("mod").getAsString(), null);
            return null;
        }

        static Entry fromFluidObject(JsonObject json) {
            if (json.has("fluid")) return of(Kind.FLUID, json.get("fluid").getAsString(), null);
            if (json.has("tag")) return of(Kind.FLUID_TAG, json.get("tag").getAsString(), null);
            if (json.has("mod")) return of(Kind.FLUID_MOD, json.get("mod").getAsString(), null);
            return null;
        }

        JsonObject toFluidObject() {
            JsonObject json = new JsonObject();
            json.addProperty(kind == Kind.FLUID_TAG ? "tag" : kind == Kind.FLUID_MOD ? "mod" : "fluid", id);
            return json;
        }

        JsonObject toObject() {
            JsonObject json = new JsonObject();
            switch (kind) {
                case ITEM -> {
                    json.addProperty("item", id);
                    if (nbt != null) json.add("nbt", nbt);
                }
                case FLUID -> json.addProperty("fluid", id);
                case FLUID_TAG -> json.addProperty("fluidTag", id);
                case FLUID_MOD -> json.addProperty("fluidMod", id);
                case TAG -> json.addProperty("tag", id);
                case MOD -> json.addProperty("mod", id);
            }
            return json;
        }

        boolean sameAs(Entry other) {
            return kind == other.kind && id.equals(other.id) && Objects.equals(
                    nbt == null ? null : nbt.toString(), other.nbt == null ? null : other.nbt.toString());
        }
    }

    // =====================================================================================
    // Widgets
    // =====================================================================================

    /** Compact checkbox-style toggle. */
    private static class SettingToggle extends AbstractButton {
        boolean value;
        private final java.util.function.Consumer<Boolean> onChange;

        SettingToggle(int x, int y, int w, int h, Component label, boolean value, java.util.function.Consumer<Boolean> onChange) {
            super(x, y, w, h, label);
            this.value = value;
            this.onChange = onChange;
        }

        @Override
        public void onPress() {
            value = !value;
            onChange.accept(value);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            var font = Minecraft.getInstance().font;
            int x = getX(), y = getY();
            if (isHoveredOrFocused()) g.fill(x, y, x + width, y + height, 0x22FFFFFF);
            int bx = x + 2, by = y + (height - 10) / 2;
            g.fill(bx, by, bx + 10, by + 10, value ? 0xFF3FA557 : 0xFF5A5A66);
            g.fill(bx + 1, by + 1, bx + 9, by + 9, value ? 0xFF5CD67A : 0xFF24242C);
            if (value) g.fill(bx + 3, by + 3, bx + 7, by + 7, 0xFFFFFFFF);
            String label = font.plainSubstrByWidth(getMessage().getString(), width - 18);
            g.drawString(font, label, bx + 14, y + (height - 8) / 2, value ? C_TEXT : C_MUTED, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
