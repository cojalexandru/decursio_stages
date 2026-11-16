package com.decursioteam.decursio_stages.client.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Supplier;

import static com.decursioteam.decursio_stages.client.screens.RestrictionEditorTheme.C_ERR;
import static com.decursioteam.decursio_stages.client.screens.RestrictionEditorTheme.C_TEXT;

/** A field-sized error indicator with an explanation; no extra modal dialogs. */
final class EditorValidation {
    final int x, y, width, height;
    final Supplier<String> check;
    final EditBox input;
    final Component help, placeholder;
    final boolean line;
    String error;

    EditorValidation(int x, int y, int width, int height, EditBox input, Component help, Component placeholder, Supplier<String> check, boolean line) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.input = input; this.help = help; this.placeholder = placeholder == null ? Component.empty() : placeholder; this.check = check; this.line = line;
        refresh();
    }

    void refresh() {
        String next = check.get();
        if (input != null) {
            input.setTextColor(next == null ? C_TEXT : C_ERR);
            if (!Objects.equals(error, next)) input.setTooltip(Tooltip.create(next == null ? help : Component.literal(next)));
            if (next != null && input.getValue().isBlank() && !next.startsWith("Choose")) {
                Component hint = width < 60 ? Component.literal("!")
                        : Component.translatable("decursio_stages.gui.editor.validation.required");
                input.setHint(hint.copy().withStyle(ChatFormatting.RED));
            } else {
                input.setHint(placeholder);
            }
        }
        error = next;
    }

    void render(GuiGraphics g, Font font) {
        if (error == null) return;
        g.fill(x - 1, y - 1, x + width + 1, y, C_ERR);
        g.fill(x - 1, y + height, x + width + 1, y + height + 1, C_ERR);
        g.fill(x - 1, y, x, y + height, C_ERR);
        g.fill(x + width, y, x + width + 1, y + height, C_ERR);
        if (line) g.drawString(font, font.plainSubstrByWidth(error, width), x, y + height + 2, C_ERR, false);
    }
}
