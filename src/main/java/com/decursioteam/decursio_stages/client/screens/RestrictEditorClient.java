package com.decursioteam.decursio_stages.client.screens;

import com.decursioteam.decursio_stages.network.messages.OpenRestrictEditorMessage;
import net.minecraft.client.Minecraft;

public final class RestrictEditorClient {
    private RestrictEditorClient() {}

    public static void handle(OpenRestrictEditorMessage message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof RestrictEditorScreen editor) {
            editor.applyServerUpdate(message);
        } else {
            mc.setScreen(new RestrictEditorScreen(message));
        }
    }
}
