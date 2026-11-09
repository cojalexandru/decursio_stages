# Decursio Stages — Minecraft 1.21.1 / NeoForge

## Migrating restrictions

Restriction files and player stage saves retain their existing locations and format, except for item data.
The `nbt` field in item restrictions, exceptions and mob loadouts now contains Minecraft data components,
using namespaced keys. Old 1.20.1 item NBT must be converted; it is not automatically migrated.
A converted damage/enchantment exception is provided in `examples/restrictions/enchanted_sword_exception.json`.
`Damage` becomes `minecraft:damage`; `Enchantments` becomes `minecraft:enchantments` with a `levels` map.
`display.Name` becomes `minecraft:custom_name`, preserving the serialized JSON text string.
For example, a custom-data rule is:

```json
{"item":"minecraft:diamond_sword","nbt":{"minecraft:custom_data":{"owner":"test_*"}}}
```

Other component keys include `minecraft:damage`, `minecraft:custom_name` and `minecraft:enchantments`.
The restriction editor and item commands capture component data from held or selected items.
Wildcard matching still supports partial compounds and string patterns.
Use NeoForge common tags such as `c:ingots/iron` in place of old Forge common tags.

KubeJS examples are in `examples/kubejs/`. Copy the scripts into the corresponding KubeJS script directory.
