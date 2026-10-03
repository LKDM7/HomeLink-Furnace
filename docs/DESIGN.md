# Implementation design

`FurnaceTier` centralizes dimensions, lane counts, constant save-format slots and
defaults. `FurnaceLayout` alone converts local column/layer/row coordinates to
world coordinates for every orientation and port. Tiers II and III store one
master BlockEntity; each other block records its local cell in block state.
An explicitly removed satellite with an unloaded master records a dimension
SavedData dismantling request. The request survives restart and is consumed once
when the matching master loads; it never loads the chunk itself. Its fingerprint
includes machine block and orientation, so a stale request cannot destroy a
different replacement. Creative / wrong-tool intent is confirmed only for an
actual removal in the same game tick and is never saved as an unconfirmed click.

The authoritative master owns input/output inventories, HE, heat, jobs,
pending outputs and XP. Each job reserves one input outside the extractible
inventories and retains its recipe ID, result snapshot, progress, paid HE,
transaction, owner and start stamp. Reload checks the same recipe and snapshot;
an invalid unfinished reservation is refunded once. A finished pending result
never turns back into raw input. Output insertion transfers ownership before a
lane is freed. No normal-running item drops are used as output overflow.

The actual datapack-sync event invalidates recipe snapshots before the next
production tick, including a job one tick from completion. Bounded periodic
checks additionally detect direct RecipeManager replacement in tools / tests.
The scheduler rotates its input cursor and only searches dirty / bounded idle
inputs. No chunk scan, force load, per-frame block update or full per-tick NBT
sync is needed. Menu synchronization transports 29 bounded scalar values when
changed, alongside standard inventory synchronization; clients render progress
and cosmetic heat locally. Cached HE / item capabilities refuse transfers when
their master is removed, so an obsolete external port cannot mutate unloaded
inventories.
Redstone IGNORE skips signal reads entirely. The other modes inspect only loaded
neighbor and conductor-source chunks, preserving vanilla signal behavior while
avoiding implicit chunk loads from vanilla signal lookup helpers.

Clients use HomeLinkTheme, HomeLinkUi, HomeLinkButton, HomeLinkScreenLayout and
HomeLinkStatusTone from the official HomeCore UI Kit. There is no FurnaceTheme,
FurnaceButton or duplicate palette. Physical assets share graphite, steel,
copper and amber; ports are distinctly marked. Client-only screens / renderers
are isolated from dedicated-server classes.

## Physical models and textures

The compact tier I cabinet, wide tier II chamber and tall tier III cabinet each
have a complete continuous mesh. The asset generator clips that mesh into its
1 / 2 / 8 world cells, interpolates face UVs and omits artificial internal cut
faces. Doors, window frames and roof panels therefore continue across cell
boundaries instead of repeating a cabinet on every block. Frame corners meet
without overlapping coplanar faces.

Sixteen deterministic 64 x 64 pixel textures provide graphite panels, brushed
steel, copper fittings, ceramic chamber lining, cold resistance bars, displays,
ventilation and port markings. Recessed chambers sit behind cutout glass with
sparse reflections. Input, output and HE connectors have their own raised
fittings on the existing dedicated port faces. Tier III has a large upper
window and two lower fans; tiers I and II have one smaller front fan.

The client renderer uses one heat mesh per tier, aligned to the complete
cabinet, and renders amber resistance bars only in READY or PROCESSING.
Fans rotate locally from client time. Cached rendering bounds include the
entire multiblock, so the large chamber and remote fan remain visible when
the master block is outside the camera frustum. No animation packets or
per-frame block updates are introduced. Item models depict the whole machine.

Regenerate visual assets in PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/generate-furnace-assets.ps1
```

By default this changes only textures, models and blockstates. The optional
`-IncludeGameplayResources` switch also regenerates the machine recipes,
loot tables and mining tags; it is not needed for visual revisions.

Machine breaking releases owned input, output, reservations and pending
results exactly once, loses internal HE, and releases stored whole XP in orbs.
Creative breaking produces no machine item. Survival machine-item loot requires
a valid tool. Fractional XP remains stored until a whole orb value is available;
sub-unit remainder is lost on permanent destruction, avoiding random duplication.
XP uses each recipe float's canonical decimal text in bounded BigDecimal storage:
ten 0.7-XP iron operations give exactly 7 XP. Saves retain `xpExact` plus a legacy
numeric value; malformed or oversized exact values use a safe bounded fallback.
