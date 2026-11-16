package com.decursioteam.decursio_stages.mixin;

import com.decursioteam.decursio_stages.orestaging.OrePackets;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class OrePacketMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> decstages$view(Packet<?> packet) {
        return (Object) this instanceof ServerGamePacketListenerImpl listener ? OrePackets.rewrite(listener.player, packet) : packet;
    }
}
