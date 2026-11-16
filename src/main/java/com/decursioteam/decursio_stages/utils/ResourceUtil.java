package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.restrictions.DimensionRestriction;
import com.decursioteam.decursio_stages.restrictions.FluidRestriction;
import com.decursioteam.decursio_stages.restrictions.ItemExclusion;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Set;

public class ResourceUtil {

    /**
     * Whether a fluid is locked by a restriction: either through its fluidList (fluid, tag or mod entry)
     * or because the fluid's mod is in the restriction's modList (same as items from that mod).
     */
    private static boolean isFluidExcepted(String restriction, Fluid fluid, Identifier id) {
        for (ItemExclusion exception : getExceptions(restriction)) {
            if (id.getNamespace().equals(exception.getFluidMod())) return true;
            if (exception.getFluid() != null && BuiltInRegistries.FLUID.containsKey(exception.getFluid())
                    && fluid.isSame(BuiltInRegistries.FLUID.getValue(exception.getFluid()))) return true;
            if (exception.getFluidTag() != null
                    && fluid.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, exception.getFluidTag()))) return true;
        }
        return false;
    }

    public static boolean isFluidRestricted(String restriction, Fluid fluid) {
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) return false;
        if (isFluidExcepted(restriction, fluid, id)) return false;
        if (getMods(restriction).contains(id.getNamespace())) return true;
        for (FluidRestriction entry : RestrictionsData.getRegistry().compiled(restriction).fluids) {
            if (entry.getFluid() != null) {
                if (fluid.isSame(entry.getFluid())) return true;
            } else if (entry.getTag() != null) {
                if (fluid.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, entry.getTag()))) return true;
            } else if (entry.getMod() != null) {
                if (id.getNamespace().equals(entry.getMod())) return true;
            }
        }
        return false;
    }

    /** Read-only collection views, rebuilt together with the restriction registry. */
    public static Set<String> getMods(String restriction) {
        return RestrictionsData.getRegistry().compiled(restriction).mods;
    }

    public static Set<ItemExclusion> getExceptions(String restriction) {
        return RestrictionsData.getRegistry().compiled(restriction).exceptions;
    }

    public static Set<DimensionRestriction> getDimensions(String restriction) {
        return RestrictionsData.getRegistry().compiled(restriction).dimensions;
    }

    public static Set<String> getContainers(String restriction) {
        return RestrictionsData.getRegistry().compiled(restriction).containers;
    }

    public static boolean getApplyToFakePlayer(String restriction) {
        return RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getApplyToFakePlayers();
    }

    public static Identifier getRegistryName(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    public static boolean check(String restriction, ItemStack itemStack, CHECK_TYPES checkType) {
        return RestrictionsData.getRegistry().compiled(restriction).matches(itemStack, checkType);
    }

    /** Historical API: true means the stack is NOT excepted. */
    public static boolean isItemExcluded(String restriction, ItemStack itemStack) {
        return !RestrictionsData.getRegistry().compiled(restriction).excepted(itemStack);
    }

    public static JsonObject itemStackToJson(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("item", getRegistryName(stack.getItem()).toString());

        if (ItemDataUtil.has(stack)) {
            json.add("nbt", NBTComparisonUtil.nbtToJson(ItemDataUtil.get(stack)));
        }

        return json;
    }

    public enum CHECK_TYPES {
        MOD,
        ITEM,
        TAG,
        ALL
    }
}