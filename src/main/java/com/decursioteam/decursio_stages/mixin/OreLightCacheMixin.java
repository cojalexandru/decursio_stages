package com.decursioteam.decursio_stages.mixin;
import com.decursioteam.decursio_stages.orestaging.OreViewCache;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerChunkCache.class)
public abstract class OreLightCacheMixin {
    @Inject(method = "onLightUpdate", at = @At("HEAD"))
    private void decstages$lightChanged(LightLayer layer, SectionPos section, CallbackInfo callback) {
        if (layer == LightLayer.BLOCK)
            OreViewCache.changed(((ServerChunkCache) (Object) this).level, section.chunk());
    }
}
