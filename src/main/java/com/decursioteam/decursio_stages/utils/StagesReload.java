package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class StagesReload implements PreparableReloadListener {

    @Override
    public @NotNull CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profilerFiller, @NotNull ProfilerFiller profilerFiller1, @NotNull Executor executor, @NotNull Executor executor1) {
        return CompletableFuture.completedFuture(null).thenCompose(preparationBarrier::wait).thenAcceptAsync(x -> {
            RestrictionsData.getRegistry().clearRawRestrictionsData();
            Registry.setupRestrictions();
            Registry.registerRestrictionsList();
            DecursioStages.LOGGER.info(Component.translatable("decursio_stages.commands.reloadstages").getString());
        }, executor1);
    }
}
