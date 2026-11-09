// Copy into kubejs/server_scripts/ and run /reload.
DecursioStagesEvents.restrictions(event => {
  // Dimension entry is denied until the player has age_2.
  event.create('script_dimensions', 'age_2')
    .dimension('minecraft:the_nether', 'Unlock Age 2 before entering the Nether.')
    .dimension('minecraft:the_end') // uses the default denial message

  // Structure rules apply while the player HAS this stage.
  // Grant adventure_rules to enable these protections; remove it to disable them.
  event.create('script_structure_rules', 'adventure_rules')
    .structure('minecraft:jungle_pyramid', {
      can_use_block: true,
      can_place_block: false,
      can_break_block: false,
      can_place_block_list: ['minecraft:torch'],
      can_break_block_list: ['minecraft:cobweb']
    })

  // Mob rules also apply while the nearby player HAS the stage.
  event.create('script_mob_rules', 'adventure_rules')
    .mob('minecraft:creeper') // BLACKLIST + NATURAL: deny natural creeper spawns
    .mob('minecraft:zombie', {
      whitelist_blacklist: 'WHITELIST',
      spawnType: ['NATURAL', 'SPAWNER'],
      health: 40,
      effects: [{ effect: 'minecraft:speed', amplifier: 0, duration: 600, chance: 50 }],
      loadout: [{ item: 'minecraft:iron_sword', slot: 'MAINHAND', chance: 50 }]
    })
  // Also supported: .mobMod('mod_id', options), .mobTag('namespace:entity_tag', options).
  // Other mob options: minLight, maxLight, biomes (an array of biome IDs).

  // Containers use fully qualified Java menu class names (not IDs like minecraft:chest).
  // Existing container rules control restricted-item handling inside these menus;
  // they do not by themselves prevent the menu from opening.
  event.create('script_container_rules', 'age_2')
    .item('minecraft:diamond')
    .container('net.minecraft.world.inventory.ChestMenu')
    .container('decursio_stages.inventoryMenu.CraftingGrid')
    .setting('containerListWhitelist', false)
  // Enable debugMode in the mod config to print a menu's class name when opened.
})
