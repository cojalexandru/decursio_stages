package com.decursioteam.decursio_stages.config;

import com.decursioteam.decursio_stages.DecursioStages;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CommonConfig {
    public static final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec config;
    public static ForgeConfigSpec.BooleanValue debugMode;

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
                                     "display": {
                                         "Name": "[{\\"text\\":\\"Magic Chest\\",\\"italic\\":false}]"
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

    public static void loadConfig(ForgeConfigSpec config, String path) {
        DecursioStages.LOGGER.info("Loading config: " + path);
        final CommentedFileConfig file = CommentedFileConfig.builder(new File(path)).sync().autosave().writingMode(WritingMode.REPLACE).build();
        DecursioStages.LOGGER.info("Built config: " + path);
        file.load();
        DecursioStages.LOGGER.info("Loaded config: " + path);
        config.setConfig(file);
    }
}
