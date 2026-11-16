package com.decursioteam.decursio_stages.mixin;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(CampfireBlockEntity.class)
public interface CampfireProgress {
    @Accessor("cookingProgress") int[] decstages$progress();
    @Accessor("cookingTime") int[] decstages$times();
}
