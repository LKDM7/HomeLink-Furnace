# Crafting and production recipes

Machine crafting uses existing HomeCore electronics and vanilla materials.
Tier II consumes Tier I; Tier III consumes Tier II. No screw, plate or heating
coil material is added. Canonical machine IDs are
`homelink_furnace:industrial_furnace_1`, `_2` and `_3`.

| Machine | Ingredients |
| --- | --- |
| I | 1 furnace, 1 Circuit Board, 1 Control Module, 4 iron ingots, 1 copper ingot, 1 redstone |
| II | 1 Furnace I, 1 Microprocessor, 1 Control Module, 1 Communication Module, 2 gold ingots, 2 iron ingots, 1 copper ingot |
| III | 1 Furnace II, 2 Control Modules, 1 Communication Module, 1 diamond, 1 netherite ingot, 2 iron blocks, 1 copper block |

Tier I uses one copper ingot so all ingredients fit the vanilla nine-cell
crafting grid. These are shaped recipes; use the in-game recipe book or a viewer
for their layouts.

The server RecipeManager resolves only `RecipeType.SMELTING`. Vanilla ores,
glass, food, cactus and valid SMELTING datapacks are supported. Jobs retain the
original recipe ID and snapshot: after `/reload`, a removed or incompatible
unfinished recipe is cancelled and its reserved input returned once, without
switching silently to a different matching recipe. Finished pending results
remain finished through reload.

The final resource JSON is authoritative for machine patterns and quantities.
Optional viewers expose vanilla smelting workstations rather than duplicating
the entire recipe set in a separate processing category.
