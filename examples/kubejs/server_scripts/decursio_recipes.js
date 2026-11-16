// Locks crafting by recipe ID, mod namespace, or output item tag; owning or using the output item stays independent.
DecursioStagesEvents.restrictions(event => {
  event.create('diamond_crafting', 'age_2')
    .recipe('minecraft:diamond_sword')
    .recipe('minecraft:diamond_pickaxe')
    .exceptRecipe('minecraft:diamond_sword') // Exception applies only within this rule.

  event.create('planks_and_mod_recipes', 'age_1')
    .recipeTag('minecraft:planks')
    .recipeMod('mekanism')
    .exceptRecipe('mekanism:basic_bin')
})
