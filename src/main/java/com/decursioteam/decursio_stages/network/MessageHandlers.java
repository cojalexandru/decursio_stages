package com.decursioteam.decursio_stages.network;

import com.decursioteam.decursio_stages.client.screens.RestrictEditorClient;
import com.decursioteam.decursio_stages.network.messages.OpenRestrictEditorMessage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageHandlers {

    /** Already runs on the main thread (registered as an enqueued message). */
    public static void handleOpenRestrictEditor(OpenRestrictEditorMessage message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RestrictEditorClient.handle(message));
    }
}
