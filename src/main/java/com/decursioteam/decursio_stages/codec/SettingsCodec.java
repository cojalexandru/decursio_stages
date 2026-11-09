package com.decursioteam.decursio_stages.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

public class SettingsCodec {

    public static final SettingsCodec DEFAULT = new SettingsCodec("ALWAYS", "", 60,true, false, true, true, false, false, false,false, false);

    public static final Codec<SettingsCodec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("advancedTooltips").orElse("ALWAYS").forGetter(SettingsCodec::getAdvancedTooltips),
            Codec.STRING.fieldOf("itemsTitle").orElse("").forGetter(SettingsCodec::getItemTitle),
            Codec.INT.fieldOf("itemsPickupDelay").orElse(60).forGetter(SettingsCodec::getPickupDelay),
            Codec.BOOL.fieldOf("hideInJEI_REI").orElse(true).forGetter(SettingsCodec::getHideInJEI),
            Codec.BOOL.fieldOf("canPickupItems").orElse(false).forGetter(SettingsCodec::getCanPickup),
            Codec.BOOL.fieldOf("dropItemsFromInventory").orElse(true).forGetter(SettingsCodec::getCheckPlayerInventory),
            Codec.BOOL.fieldOf("dropArmorFromInventory").orElse(true).forGetter(SettingsCodec::getCheckPlayerEquipment),
            Codec.BOOL.fieldOf("canUseItems").orElse(false).forGetter(SettingsCodec::getUsableItems),
            Codec.BOOL.fieldOf("canRightClickBlocks").orElse(false).forGetter(SettingsCodec::getUsableBlocks),
            Codec.BOOL.fieldOf("canBreakBlocks").orElse(false).forGetter(SettingsCodec::getDestroyableBlocks),
            Codec.BOOL.fieldOf("containerListWhitelist").orElse(false).forGetter(SettingsCodec::getHideInJEI),
            Codec.BOOL.fieldOf("applyToFakePlayers").orElse(false).forGetter(SettingsCodec::getApplyToFakePlayers)
    ).apply(instance, SettingsCodec::new));

    protected final String advancedTooltips;
    protected final String itemTitle;
    protected final int pickupDelay;
    protected final boolean hideInJEI;
    protected final boolean canPickup;
    protected final boolean checkPlayerInventory;
    protected final boolean checkPlayerEquipment;
    protected final boolean usableItems;
    protected final boolean usableBlocks;
    protected final boolean destroyableBlocks;
    protected final boolean containerListWhitelist;
    protected final boolean applyToFakePlayers;

    private SettingsCodec(String advancedTooltips, String itemTitle, int pickupDelay, boolean hideInJEI, boolean canPickup, boolean checkPlayerInventory, boolean checkPlayerEquipment, boolean usableItems, boolean usableBlocks, boolean destroyableBlocks, boolean containerListWhitelist, boolean applyToFakePlayers){
        this.advancedTooltips = advancedTooltips;
        this.itemTitle = itemTitle;
        this.pickupDelay = pickupDelay;
        this.hideInJEI = hideInJEI;
        this.canPickup = canPickup;
        this.checkPlayerInventory = checkPlayerInventory;
        this.checkPlayerEquipment = checkPlayerEquipment;
        this.usableItems = usableItems;
        this.usableBlocks = usableBlocks;
        this.destroyableBlocks = destroyableBlocks;
        this.containerListWhitelist = containerListWhitelist;
        this.applyToFakePlayers = applyToFakePlayers;
    }

    public String getAdvancedTooltips() {
        return advancedTooltips;
    }

    public boolean getApplyToFakePlayers() {
        return applyToFakePlayers;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public int getPickupDelay() {
        return pickupDelay;
    }

    public boolean getHideInJEI() {
        return hideInJEI;
    }

    public boolean getContainerListWhitelist() {
        return containerListWhitelist;
    }

    public boolean getCanPickup() {
        return canPickup;
    }

    public boolean getCheckPlayerInventory() {
        return checkPlayerInventory;
    }

    public boolean getCheckPlayerEquipment() {
        return checkPlayerEquipment;
    }

    public boolean getUsableItems() {
        return usableItems;
    }

    public boolean getUsableBlocks() {
        return usableBlocks;
    }

    public boolean getDestroyableBlocks() {
        return destroyableBlocks;
    }

    public SettingsCodec toImmutable() {
        return this;
    }

    public static class Mutable extends SettingsCodec {

        public Mutable(String advancedTooltips, String itemTitle, int pickupDelay, boolean hideInJEI, boolean canPickup, boolean checkPlayerInventory, boolean checkPlayerEquipment, boolean usableItems, boolean usableBlocks, boolean destroyableBlocks, boolean containerListWhitelist, boolean applyToFakePlayers) {
            super(advancedTooltips, itemTitle, pickupDelay, hideInJEI, canPickup, checkPlayerInventory, checkPlayerEquipment, usableItems, usableBlocks, destroyableBlocks, containerListWhitelist, applyToFakePlayers);
        }

        @Override
        public SettingsCodec toImmutable() {
            Player player;
            return new SettingsCodec(this.advancedTooltips, this.itemTitle, this.pickupDelay, this.hideInJEI, this.canPickup, this.checkPlayerInventory, this.checkPlayerEquipment, this.usableItems, this.usableBlocks, this.destroyableBlocks, this.containerListWhitelist, this.applyToFakePlayers);
        }
    }
}

