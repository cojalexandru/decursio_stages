package com.decursioteam.decursio_stages.utils;

import com.decursioteam.decursio_stages.restrictions.ItemRestriction;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.regex.Pattern;

public class NBTComparisonUtil {
    public static final String WILDCARD_MARKER = "*";

    public static boolean areNBTCompoundsEqual(CompoundTag tag1, CompoundTag tag2) {
        if (tag1 == tag2) return true;
        if (tag1 == null || tag2 == null) return false;

        for (String key : tag1.getAllKeys()) {
            if (!tag2.contains(key)) return false;
            if (!areNBTElementsEqual(tag1.get(key), tag2.get(key), true)) return false;
        }
        return true;
    }

    public static boolean areNBTElementsEqual(Tag element1, Tag element2, boolean element1IsPattern) {
        if (element1 == element2) return true;
        if (element1 == null || element2 == null) return false;

        if (element1 instanceof StringTag && element1IsPattern) {
            String str = ((StringTag) element1).getAsString();
            if (WILDCARD_MARKER.equals(str)) {
                return true;
            } else if (str.contains(WILDCARD_MARKER)) {
                if (!(element2 instanceof StringTag)) return false;
                String pattern = str.replace(WILDCARD_MARKER, ".*");
                return Pattern.compile(pattern).matcher(((StringTag) element2).getAsString()).matches();
            }
        }

        if (element1 instanceof ByteTag && element2 instanceof ByteTag) {
            byte b1 = ((ByteTag) element1).getAsByte();
            byte b2 = ((ByteTag) element2).getAsByte();

            // If both are 0 or 1, treat them as boolean values
            if ((b1 == 0 || b1 == 1) && (b2 == 0 || b2 == 1)) {
                return b1 == b2;
            }
        }

        if (element1 instanceof NumericTag && element2 instanceof NumericTag) {
            NumericTag num1 = (NumericTag) element1;
            NumericTag num2 = (NumericTag) element2;

            if (element1 instanceof ByteTag && element2 instanceof ByteTag) {
                byte b1 = ((ByteTag) element1).getAsByte();
                byte b2 = ((ByteTag) element2).getAsByte();

                if ((b1 == 0 || b1 == 1) && (b2 == 0 || b2 == 1)) {
                    return b1 == b2;
                }
            }

            if (isIntegerType(num1) && isIntegerType(num2)) {
                return num1.getAsLong() == num2.getAsLong();
            }

            return num1.getAsDouble() == num2.getAsDouble();
        }


        if (element1.getClass() != element2.getClass()) {

            if (element1 instanceof ByteTag) {
                byte value = ((ByteTag) element1).getAsByte();
                if (value == 0 || value == 1) {
                    boolean boolValue = (value == 1);

                    if (element2 instanceof StringTag) {
                        String strValue = ((StringTag) element2).getAsString();
                        if (strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("false")) {
                            return Boolean.parseBoolean(strValue) == boolValue;
                        }
                    }
                }
            }

            if (element2 instanceof ByteTag) {
                byte value = ((ByteTag) element2).getAsByte();
                if (value == 0 || value == 1) {
                    boolean boolValue = (value == 1);

                    if (element1 instanceof StringTag) {
                        String strValue = ((StringTag) element1).getAsString();
                        if (strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("false")) {
                            return Boolean.parseBoolean(strValue) == boolValue;
                        }
                    }
                }
            }

            return false;
        }

        if (element1 instanceof CompoundTag) {
            return areNBTCompoundsEqual((CompoundTag) element1, (CompoundTag) element2);
        }
        else if (element1 instanceof ListTag) {
            ListTag list1 = (ListTag) element1;
            ListTag list2 = (ListTag) element2;

            if (list1.isEmpty() && element1IsPattern) {
                return true;
            }

            if (list1.size() != list2.size()) return false;

            for (int i = 0; i < list1.size(); i++) {
                if (!areNBTElementsEqual(list1.get(i), list2.get(i), element1IsPattern)) {
                    return false;
                }
            }
            return true;
        }
        else {
            return element1.equals(element2);
        }
    }

    private static boolean isIntegerType(NumericTag tag) {
        return tag instanceof ByteTag || tag instanceof ShortTag ||
                tag instanceof IntTag || tag instanceof LongTag;
    }

    public static JsonElement nbtToJson(CompoundTag nbt) {
        if (nbt == null) return null;

        // Use Minecraft's built-in conversion
        return Dynamic.convert(
                NbtOps.INSTANCE,
                JsonOps.INSTANCE,
                nbt
        );
    }

    public static CompoundTag jsonToNbt(JsonElement json) {
        if (json == null) return null;

        Tag nbtTag = Dynamic.convert(
                JsonOps.INSTANCE,
                NbtOps.INSTANCE,
                json
        );

        if (nbtTag instanceof CompoundTag) {
            return (CompoundTag) nbtTag;
        }

        CompoundTag result = new CompoundTag();
        result.put("value", nbtTag);
        return result;
    }

    public static CompoundTag parseJsonToNbt(String jsonString) {
        try {
            JsonElement json = JsonParser.parseString(jsonString);
            return jsonToNbt(json);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean doesItemMatchRestriction(ItemRestriction restrictionItem, ItemStack itemStack) {
        if (!restrictionItem.getItemStack().is(itemStack.getItem())) {
            return false;
        }

        if (restrictionItem.getCompoundNBT() == null) {
            return true;
        }

        if (itemStack.getTag() == null) {
            return false;
        }

        return areNBTCompoundsEqual(restrictionItem.getCompoundNBT(), itemStack.getTag());
    }

    public static CompoundTag createNbtWithWildcard(CompoundTag nbt, String path) {
        if (nbt == null) return null;

        CompoundTag result = nbt.copy();

        String[] parts = path.split("\\.");
        CompoundTag current = result;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];

            if (!current.contains(part, Tag.TAG_COMPOUND)) {
                CompoundTag newTag = new CompoundTag();
                current.put(part, newTag);
                current = newTag;
            } else {
                current = current.getCompound(part);
            }
        }

        current.putString(parts[parts.length - 1], WILDCARD_MARKER);

        return result;
    }

    public static boolean hasNbtPath(CompoundTag nbt, String path) {
        if (nbt == null) return false;

        String[] parts = path.split("\\.");
        CompoundTag current = nbt;

        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.contains(parts[i], Tag.TAG_COMPOUND)) {
                return false;
            }
            current = current.getCompound(parts[i]);
        }

        return current.contains(parts[parts.length - 1]);
    }

    public static Tag getNbtByPath(CompoundTag nbt, String path) {
        if (nbt == null) return null;

        String[] parts = path.split("\\.");
        CompoundTag current = nbt;

        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.contains(parts[i], Tag.TAG_COMPOUND)) {
                return null;
            }
            current = current.getCompound(parts[i]);
        }

        return current.get(parts[parts.length - 1]);
    }
}