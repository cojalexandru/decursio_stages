// Copy into kubejs/server_scripts/. Restart once after installing the mod;
// then use /reload after editing scripts.
DecursioStagesEvents.restrictions(event => {
  event.create('script_diamond_tools', 'age_2')
    .item('minecraft:diamond_pickaxe')
    .item('minecraft:diamond_sword')
    .setting('hideInJEI_REI', true)
    .setting('canPickupItems', true)
    .setting('dropItemsFromInventory', false)
    .setting('dropArmorFromInventory', false)
    .setting('itemsTitle', 'Requires Age 2')

  // Lock vanilla fluids, but leave water visible.
  event.create('script_fluids', 'age_2')
    .fluidMod('minecraft')
    .exceptFluid('minecraft:water')

  // Also available: .tag('forge:ingots/iron'), .mod('create'),
  // .fluid('minecraft:lava'), .fluidTag('minecraft:water'),
  // .exceptItem(...), .exceptTag(...), .exceptMod(...),
  // .exceptFluidTag(...), .exceptFluidMod(...).

  // custom() accepts the same complete JSON as restriction config files,
  // including dimensions, structures, mobs, containers and item NBT.
  event.custom('script_custom', {
    'Restriction Data': {
      stage: 'age_2',
      itemList: [{ item: 'minecraft:diamond_axe' }],
      exceptionList: []
    },
    Settings: { hideInJEI_REI: true, canPickupItems: true }
  })
})
