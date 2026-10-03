# Validation

This file records executed checks, not planned acceptance criteria.

Current audited environment: Windows / PowerShell, Java 21, Minecraft 1.21.1,
NeoForge 21.1.252, HomeCore 1.14.0 / API 1.9.0, HomeLink Energy 0.5.0.
Real Storage Pipes exist in the adjacent HomeLink Storage 1.4.0 repository.

## Executed checks (2026-10-03)

| Check | Observed result |
| --- | --- |
| Unit tests after visual revision | 26 passed; 0 failed, 0 skipped in `build/test-results/test` |
| Dedicated server GameTests with actual Storage | 71 required tests passed in 2.429 s; 0 failed |
| Build / test / verification compile / release JAR check | Final BUILD SUCCESSFUL against actual local HomeCore, Energy and optional Storage sources |
| Client smoke after visual revision with JEI | `FURNACE_SMOKE_OK steps=77`; 19 actual screenshots captured; slot/widget overlap checks and real slot clicks passed |
| JEI | Actual runtime passed: 3 SMELTING catalysts and 3 information displays, version 19.57.0.449 |
| REI | Actual runtime passed: 3 SMELTING workstations and 3 information displays, version 16.0.799; client smoke 77 steps |
| REI smoke after final server fixes, before visual revision | `FURNACE_SMOKE_OK steps=77`; real slot-click conservation passed |

The visual revision was rebuilt and exercised in the actual JEI client after
regenerating the final meshes. The refreshed captures show all three tiers
from front, rear and above, plus the hot tier III chamber. Manual inspection
found no missing textures or visible cell seams in those views. The new resource
check validates world, item, fan and heat model geometry, finite coordinates,
UV bounds, local parent chains, texture aliases and actual PNG decoding.
GameTests and the REI smoke were not rerun for this visual-only revision;
their results above record the preceding machine implementation checks.

The GameTests cover all 49 requested scenarios plus real injected SMELTING
recipes, three-item receipts, hidden-tab click rejection, XP authorization,
exact decimal XP after ten iron operations, normal-production event behavior,
machine crafting recipes, inert cached ports after removal, malformed XP NBT,
warning events across OFF/ON, no FE capability, deferred dismantling SavedData,
same-tick recipe-reload invalidation, final cold-state dirty persistence,
redstone reads without loading adjacent chunks, an actual Energy network and actual Storage Pipe
transport through INPUT and OUTPUT. A detached-copy comparison is complemented
by live BE reloads followed by completed production / pending transfer.

Evidence: `build/validation/gametest/logs/latest.log`,
`build/validation/client/logs/latest.log`, `build/validation/evidence/jei/latest.log`,
`build/validation/evidence/rei/latest.log`, `build/test-results/test`, and
`build/validation/client/screenshots`. Nineteen actual screenshots were captured.
Build artifacts are local; 19 shareable captures are copied into
[docs/screenshots](screenshots/README.md).

The pure-job benchmark covers 50 and 100 eight-lane simulations for 2,000 ticks,
with conservation assertions and a generated report at
`build/reports/furnace-core-benchmark.json`. It excludes world, heat, inventories,
HomeCore events, Energy distribution and networking. It does **not** measure
Minecraft TPS or real-world machine throughput.

Compilation and automated client clicks do not establish exhaustive human
review of narration, every modpack recipe or every external viewer interaction.
The GitHub CI workflow has been written and reviewed, but has not run remotely.

Commands executed through the supplied Windows wrapper:

```powershell
.\gradlew.bat build test verifyReleaseJar runGameTestServer -PwithStorage --no-configuration-cache
.\gradlew.bat runSmoke -PwithJei --no-configuration-cache
.\gradlew.bat runSmoke -PwithRei --no-configuration-cache
```

French implementation details and all 20 requested reporting points are in
[IMPLEMENTATION_REPORT](IMPLEMENTATION_REPORT.md).
