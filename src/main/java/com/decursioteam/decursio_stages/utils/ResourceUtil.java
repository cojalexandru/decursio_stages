package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.restrictions.DimensionRestriction;
import com.decursioteam.decursio_stages.restrictions.FluidRestriction;
import com.decursioteam.decursio_stages.restrictions.ItemExclusion;
import com.decursioteam.decursio_stages.restrictions.ItemRestriction;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResourceUtil {

    /**
     * Whether a fluid is locked by a restriction: either through its fluidList (fluid, tag or mod entry)
     * or because the fluid's mod is in the restriction's modList (same as items from that mod).
     */
    private static boolean isFluidExcepted(String restriction, Fluid fluid, ResourceLocation id) {
        for (ItemExclusion exception : getExceptions(restriction)) {
            if (id.getNamespace().equals(exception.getFluidMod())) return true;
            if (exception.getFluid() != null && ForgeRegistries.FLUIDS.containsKey(exception.getFluid())
                    && fluid.isSame(ForgeRegistries.FLUIDS.getValue(exception.getFluid()))) return true;
            if (exception.getFluidTag() != null
                    && fluid.is(Objects.requireNonNull(ForgeRegistries.FLUIDS.tags()).createTagKey(exception.getFluidTag()))) return true;
        }
        return false;
    }

    public static boolean isFluidRestricted(String restriction, Fluid fluid) {
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        if (id == null) return false;
        if (isFluidExcepted(restriction, fluid, id)) return false;
        if (getMods(restriction).contains(id.getNamespace())) return true;
        for (FluidRestriction entry : getFluids(restriction)) {
            if (entry.getFluid() != null) {
                if (fluid.isSame(entry.getFluid())) return true;
            } else if (entry.getTag() != null) {
                if (fluid.is(Objects.requireNonNull(ForgeRegistries.FLUIDS.tags()).createTagKey(entry.getTag()))) return true;
            } else if (entry.getMod() != null) {
                if (id.getNamespace().equals(entry.getMod())) return true;
            }
        }
        return false;
    }

    public static Set<ItemRestriction> getItems(String restriction) {
        return new HashSet<>(RestrictionsData.getRestrictionData(restriction).getData().getItemList());
    }

    public static Set<ResourceLocation> getTags(String restriction) {
        return new HashSet<>(RestrictionsData.getRestrictionData(restriction).getData().getTagList());
    }

    public static Set<String> getMods(String restriction) {
        return new HashSet<>(RestrictionsData.getRestrictionData(restriction).getData().getModList());
    }

    public static HashSet<FluidRestriction> getFluids(String restriction) {
        return new HashSet<>(RestrictionsData.getRestrictionData(restriction).getData().getFluidList());
    }

    public static Set<ItemExclusion> getExceptions(String restriction) {
        return new HashSet<>(RestrictionsData.getRestrictionData(restriction).getData().getExceptionList());
    }

    public static Set<DimensionRestriction> getDimensions(String restriction) {
        return new HashSet<>(RestrictionsData.getRestrictionData(restriction).getData().getDimensionList());
    }

    public static Set<String> getContainers(String restriction) {
        return new HashSet<>(RestrictionsData.getRestrictionData(restriction).getData().getContainerList());
    }

    public static boolean getApplyToFakePlayer(String restriction) {
        return RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getApplyToFakePlayers();
    }

    public static ResourceLocation getRegistryName(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    public static boolean check(String restriction, ItemStack itemStack, CHECK_TYPES checkType) {
        if (getRegistryName(itemStack.getItem()).equals(new ResourceLocation("minecraft:air"))) {
            return false;
        }

        if (!isItemExcluded(restriction, itemStack)) {
            return false;
        }

        switch (checkType) {
            case ITEM: {
                for (ItemRestriction item : getItems(restriction)) {
                    if (NBTComparisonUtil.doesItemMatchRestriction(item, itemStack)) {
                        return true;
                    }
                }
                return false;
            }
            case MOD: {
                if (!getMods(restriction).isEmpty()) {
                    String itemMod = Objects.requireNonNull(getRegistryName(itemStack.getItem())).getNamespace();
                    return getMods(restriction).contains(itemMod);
                }
                return false;
            }
            case TAG: {
                if (!getTags(restriction).isEmpty()) {
                    for (ResourceLocation tagID : getTags(restriction)) {
                        if (itemStack.is(Objects.requireNonNull(ForgeRegistries.ITEMS.tags()).createTagKey(tagID))) {
                            return true;
                        }
                    }
                }
                return false;
            }
            case ALL: {
                return check(restriction, itemStack, CHECK_TYPES.ITEM) ||
                        check(restriction, itemStack, CHECK_TYPES.MOD) ||
                        check(restriction, itemStack, CHECK_TYPES.TAG);
            }
            default:
                return false;
        }
    }

    public static boolean isItemExcluded(String restriction, ItemStack itemStack) {
        AtomicBoolean isExcluded = new AtomicBoolean(false);

        getExceptions(restriction).forEach(itemExclusion -> {
            if (itemExclusion.getResourceLocation() != null) {
                if (itemExclusion.getCompoundNBT() == null) {
                    if (itemExclusion.getItemStack().is(itemStack.getItem())) {
                        isExcluded.set(true);
                    }
                } else if (itemStack.getTag() != null) {
                    if (itemExclusion.getItemStack().is(itemStack.getItem()) &&
                            NBTComparisonUtil.areNBTCompoundsEqual(itemExclusion.getCompoundNBT(), itemStack.getTag())) {
                        isExcluded.set(true);
                    }
                }
            }
            else if (itemExclusion.getMod() != null) {
                if (getRegistryName(itemStack.getItem()).getNamespace().equals(itemExclusion.getMod())) {
                    isExcluded.set(true);
                }
            }
            else if (itemExclusion.getTag() != null) {
                if (itemStack.is(Objects.requireNonNull(ForgeRegistries.ITEMS.tags()).createTagKey(itemExclusion.getTag()))) {
                    isExcluded.set(true);
                }
            }
        });

        return !isExcluded.get();
    }

    public static JsonObject itemStackToJson(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("item", getRegistryName(stack.getItem()).toString());

        if (stack.hasTag()) {
            json.add("nbt", NBTComparisonUtil.nbtToJson(stack.getTag()));
        }

        return json;
    }

    public static ItemStack jsonToItemStack(JsonObject json) {
        if (!json.has("item")) {
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(json.get("item").getAsString()));
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);

        if (json.has("nbt")) {
            CompoundTag nbt = NBTComparisonUtil.jsonToNbt(json.get("nbt"));
            if (nbt != null) {
                stack.setTag(nbt);
            }
        }

        return stack;
    }

    public static ItemRestriction createWildcardRestriction(ItemStack base, String nbtPath) {
        if (base.isEmpty()) {
            return null;
        }

        if (!base.hasTag() || "*".equals(nbtPath)) {
            return new ItemRestriction(getRegistryName(base.getItem()), null);
        }

        CompoundTag nbtCopy = base.getTag().copy();

        if (!"*".equals(nbtPath)) {
            nbtCopy = NBTComparisonUtil.createNbtWithWildcard(nbtCopy, nbtPath);
        }

        return new ItemRestriction(getRegistryName(base.getItem()), Optional.ofNullable(nbtCopy));
    }

    public enum CHECK_TYPES {
        MOD,
        ITEM,
        TAG,
        ALL
    }
}