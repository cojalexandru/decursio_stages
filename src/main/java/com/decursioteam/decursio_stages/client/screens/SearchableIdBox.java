package com.decursioteam.decursio_stages.client.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

import static com.decursioteam.decursio_stages.client.screens.RestrictionEditorTheme.*;

/** Editable ID field with a popup rendered and hit-tested above the other form widgets. */
final class SearchableIdBox extends EditBox {
    record Option(String id, String label) {
        boolean matches(String query) {
            query = query.trim().toLowerCase(Locale.ROOT);
            return id.toLowerCase(Locale.ROOT).contains(query) || label.toLowerCase(Locale.ROOT).contains(query);
        }
        String display() { return id; }
    }

    private static final int ROW = 18;
    private final Font font;
    private final List<Option> options;
    private List<Option> filtered = List.of();
    private boolean open;
    private int selected;
    private int offset;
    private int popupY;
    private int visibleRows;

    SearchableIdBox(Font font, int x, int y, int width, String hint, List<Option> options) {
        super(font, x, y, width, 20, Component.literal(hint));
        this.font = font;
        this.options = options;
    }

    void filter(String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        filtered = options.stream().filter(option -> option.matches(query)).toList();
        selected = 0;
        offset = 0;
        open = isFocused();
    }

    void browse() {
        filtered = options;
        selected = 0;
        offset = 0;
        open = true;
    }

    void closePopup() { open = false; }
    boolean popupOpen() { return open && isFocused(); }

    @Override
    public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();
        super.setFocused(focused);
        if (!focused) open = false;
        else if (!wasFocused) filter(getValue());
    }

    private void layout(int screenHeight) {
        int below = screenHeight - (getY() + getHeight()) - 4;
        int above = getY() - 4;
        boolean upwards = below < ROW * 4 && above > below;
        visibleRows = Math.max(1, Math.min(6, (upwards ? above : below) / ROW));
        visibleRows = Math.min(visibleRows, Math.max(1, filtered.size()));
        popupY = upwards ? getY() - visibleRows * ROW - 2 : getY() + getHeight() + 2;
    }

    private boolean contains(double x, double y) {
        return x >= getX() && x < getX() + getWidth() && y >= popupY && y < popupY + visibleRows * ROW;
    }

    boolean clickPopup(double x, double y, int button, int screenHeight) {
        if (!popupOpen()) return false;
        layout(screenHeight);
        if (!contains(x, y)) return false;
        int index = offset + (int) ((y - popupY) / ROW);
        if (button == 0 && index < filtered.size()) choose(index);
        return true;
    }

    boolean scrollPopup(double x, double y, double delta, int screenHeight) {
        if (!popupOpen()) return false;
        layout(screenHeight);
        if (!contains(x, y)) return false;
        offset = Math.clamp(offset - (int) Math.signum(delta), 0, Math.max(0, filtered.size() - visibleRows));
        if (!filtered.isEmpty()) selected = Math.clamp(selected, offset, Math.min(filtered.size() - 1, offset + visibleRows - 1));
        return true;
    }

    private void choose(int index) {
        setValue(filtered.get(index).id());
        moveCursorToEnd(false);
        open = false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP) {
            if (!popupOpen()) browse();
            else if (!filtered.isEmpty()) {
                selected = Math.clamp(selected + (event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, filtered.size() - 1);
                int rows = Math.max(1, visibleRows);
                if (selected < offset) offset = selected;
                if (selected >= offset + rows) offset = selected - rows + 1;
            }
            return true;
        }
        if (popupOpen()) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) { open = false; return true; }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                if (!filtered.isEmpty()) choose(selected);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_TAB) open = false;
        }
        return super.keyPressed(event);
    }

    void renderPopup(GuiGraphicsExtractor g, int mouseX, int mouseY, int screenHeight) {
        if (!popupOpen()) return;
        layout(screenHeight);
        offset = Math.min(offset, Math.max(0, filtered.size() - visibleRows));
        g.nextStratum();
        g.fill(getX() - 1, popupY - 1, getX() + getWidth() + 1, popupY + visibleRows * ROW + 1, C_ACCENT);
        g.fill(getX(), popupY, getX() + getWidth(), popupY + visibleRows * ROW, C_SECTION);
        if (filtered.isEmpty()) {
            g.text(font, font.plainSubstrByWidth("No matches; manual ID allowed", getWidth() - 8), getX() + 4, popupY + 5, C_MUTED, false);
        } else for (int row = 0; row < visibleRows; row++) {
            int index = offset + row;
            int y = popupY + row * ROW;
            boolean hovered = contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW;
            if (hovered || index == selected) g.fill(getX(), y, getX() + getWidth(), y + ROW, C_SLOT_HOVER);
            g.text(font, font.plainSubstrByWidth(filtered.get(index).display(), getWidth() - 10), getX() + 4, y + 5, C_TEXT, false);
        }
        if (filtered.size() > visibleRows) {
            int track = visibleRows * ROW;
            int thumb = Math.max(6, track * visibleRows / filtered.size());
            int y = popupY + (track - thumb) * offset / (filtered.size() - visibleRows);
            g.fill(getX() + getWidth() - 2, y, getX() + getWidth(), y + thumb, C_ACCENT);
        }
    }
}
