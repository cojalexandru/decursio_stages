package com.decursioteam.decursio_stages.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "decursio_stages", value = Dist.CLIENT)
public class HUDOverlay {

    private static String message = "";
    private static int remainingTicks = 0;
    public static void setMessage(String newMessage, int stayTicks) {
        message = newMessage;
        remainingTicks = stayTicks;
    }

    // Render the custom text on the screen
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Pre event) {
        if (remainingTicks > 0 && !message.isEmpty()) {
            renderCustomText(event.getGuiGraphics(), message);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && remainingTicks > 0) {
            remainingTicks--;
        }
    }

    private static void renderCustomText(GuiGraphics guiGraphics, String text) {
        Minecraft mc = Minecraft.getInstance();

        int screenWidth = mc.getWindow().getGuiScaledWidth();

        int x = screenWidth / 2 - mc.font.width(text) / 2;
        int y = 20;

        guiGraphics.drawString(mc.font, Component.literal(text), x, y, 0xFFFFFF, true);
        RenderSystem.disableBlend();
    }
}
