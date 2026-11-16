// Copy into kubejs/server_scripts/ and run /reload.
DecursioStagesEvents.restrictions(event => {
  // All entries inherit iron_age from this restriction.
  event.create('iron_ores', 'iron_age')
    .ore('minecraft:iron_ore', 'minecraft:stone')
    .ore('minecraft:deepslate_iron_ore', 'minecraft:deepslate')

  event.create('gold_ores', 'gold_age')
    .oreTag('minecraft:gold_ores', 'minecraft:stone')
})

// Lit and unlit redstone ore states are both disguised.
// Hidden ore lighting is replaced per player; unlocked players keep the original light.
DecursioStagesEvents.restrictions(event => {
  event.create('redstone_ores', 'redstone_age')
    .ore('minecraft:redstone_ore', 'minecraft:stone')
    .ore('minecraft:deepslate_redstone_ore', 'minecraft:deepslate')
})
