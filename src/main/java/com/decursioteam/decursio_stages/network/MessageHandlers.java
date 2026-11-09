package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.client.screens.RestrictEditorClient;
import com.decursioteam.decursio_stages.network.messages.OpenRestrictEditorMessage;

import net.neoforged.neoforge.network.handling.IPayloadContext;


public class MessageHandlers {

    /** Already runs on the main thread (registered as an enqueued message). */
    public static void handleOpenRestrictEditor(OpenRestrictEditorMessage message, IPayloadContext context) {
        RestrictEditorClient.handle(message);
    }
}
