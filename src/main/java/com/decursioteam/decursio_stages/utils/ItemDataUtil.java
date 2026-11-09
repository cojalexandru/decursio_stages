package com.decursioteam.decursio_stages.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** The restriction schema retains "nbt", whose keys are now data component IDs. */
public final class ItemDataUtil {
    private ItemDataUtil() {}
    private static HolderLookup.Provider registries() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.getDist() == Dist.CLIENT) return ClientRegistries.get();
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
    public static CompoundTag get(ItemStack stack) {
        return (CompoundTag) DataComponentMap.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries()), stack.getComponents()).getOrThrow();
    }
    public static boolean has(ItemStack stack) { return !stack.getComponentsPatch().isEmpty(); }
    public static void apply(ItemStack stack, CompoundTag components) {
        stack.applyComponents(DataComponentPatch.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries()), components).getOrThrow());
    }
    /** Wildcard patterns need not be valid component values; the editor can still show the base item. */
    public static void applyPreview(ItemStack stack, CompoundTag components) {
        DataComponentPatch.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries()), components)
                .result().ifPresent(stack::applyComponents);
    }
    private static final class ClientRegistries {
        static HolderLookup.Provider get() {
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            return connection != null ? connection.registryAccess() : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        }
    }
}
