package com.decursioteam.decursio_stages.config;

import net.neoforged.neoforge.common.ModConfigSpec;


public class CommonConfig {
    public static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
    public static final ModConfigSpec config;
    public static ModConfigSpec.BooleanValue debugMode;

    static {
        builder.push("General Options");
        debugMode = builder.comment("""
                        This is an example .json file for a restriction that takes advantage of every feature of the mod. For more information check the wiki https://decursio-team-1.gitbook.io/wiki/mods/decursio-stages
                       \s
                     {
                         "Restriction Data": {
                         "stage": "age_1",
                         "itemList": [
                             {
                                 "item": "minecraft:chest",
                                 "nbt": {
                                     "minecraft:custom_name": {
                                         "text": "Magic Chest", "italic": false
                                     }
                                 }
                             }
                         ],
                         "modList": [
                             "minecraft"
                         ],
                         "tagList": [
                             "minecraft:logs"
                         ],
                         "fluidList": [
                             {
                                 "fluid": "minecraft:water"
                             },
                             {
                                 "tag": "lava"
                             },
                             {
                                 "mod": "mekanism"
                             }
                         ],
                         "exceptionList": [
                             {
                                 "item": "minecraft:chest"
                             }
                         ],
                         "structureList": [
                             {
                             "structure": "minecraft:village_plains",
                             "can_use_block": false,
                             "can_place_block": false,
                             "can_break_block": false,
                             "can_use_block_list": ["minecraft:furnace", "minecraft:tnt"],
                             "can_place_block_list": ["minecraft:sand"],
                             "can_break_block_list": []
                             }
                         ],
                         "mobList": [
                             {
                             "spawnType": ["NATURAL", "MOB_SUMMONED", "SPAWN_EGG", "COMMAND"],
                             "whitelist_blacklist": "WHITELIST",
                             "entityID": "minecraft:zombie",
                             "health": 500,
                             "effects": [
                                 {
                                 "effect": "minecraft:strength",
                                 "amplifier": 1,
                                 "duration": 0,
                                 "chance": 100
                                 }
                             ],
                             "loadout": [
                                 {
                                 "item": "minecraft:egg",
                                 "slot": "MAINHAND",
                                 "chance": 100
                                 }
                             ]
                             },
                             {
                             "spawnType": ["NATURAL", "MOB_SUMMONED", "SPAWN_EGG"],
                             "whitelist_blacklist": "BLACKLIST",
                             "entityID": "minecraft:skeleton"
                             }
                         ],
                         "containerList": [
                             "net.minecraft.inventory.container.WorkbenchContainer"
                         ],
                         "dimensionList": [
                             {
                             "dimension": "minecraft:the_nether",
                             "message": "You're not allowed in this dimension!"
                             }
                         ]
                         },
                         "Settings": {
                         "advancedTooltips": "ALWAYS",
                         "dropItemsFromInventory": true,
                         "dropArmorFromInventory": false,
                         "canPickupItems": false,
                         "itemsPickupDelay": 15,
                         "hideInJEI_REI": true,
                         "canUseItems": false,
                         "containerListWhitelist": false,
                         "canBreakBlocks": false,
                         "canRightClickBlocks": false,
                         "applyToFakePlayers": false,
                         }
                     }
                       \s
                        Set this value to 'true' whenever you're having issues, this will allow the mod to output everything it tries to do in the log.""")
                .define("debugMode", false);

        builder.pop();
        config = builder.build();
    }

}
