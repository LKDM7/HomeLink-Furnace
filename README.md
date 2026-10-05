# HomeLink Furnace

Industrial electric furnaces for the HomeLink ecosystem. V1 processes actual
server SMELTING recipes in parallel, including compatible datapack and mod recipes.

**HomeLink Furnace accepts no fuel.** There is no fuel slot, FE/RF
conversion, energy generation, offline catch-up or forced chunk loading.

Version **0.1.0**, All Rights Reserved, author LKDM. Requires Minecraft **1.21.1**, Java **21**,
NeoForge **21.1.252**, **HomeCore 1.14.0** (Dashboard API 1.9.0) and **HomeLink Energy
0.5.0**. Install the three separate mod JARs. HomeCore and Energy are not bundled.

| Tier | Footprint | Lanes | Speed | Input / output slots | HE buffer |
| --- | --- | --- | --- | --- | --- |
| I | 1×1×1 | 2 | ×1.00 | 9 / 9 | 2,000 |
| II | 2×1×1 | 4 | ×1.50 | 18 / 18 | 6,000 |
| III | 2×2×2 | 8 | ×2.00 | 27 / 27 | 16,000 |

A standard 200-tick recipe costs 100 HE per operation on every tier. Speed
changes elapsed ticks; higher tiers increase throughput and capacity. A cold
machine needs HE for warm-up before production. Finished output remains
extractible when power is disabled.

The facade opens Production, Energy and Settings using HomeCore's official UI
Kit. Production contains the item inventories, parallel lanes and XP collection.
The integrated `?` manual is available in French and English. Energy reports
HE storage and actual draw; Settings controls power, redstone and the recorded
HomeNetwork using HomeCore authorization.

Physical ports are centralized in `FurnaceLayout`: input is on top, output is
at the back and HE enters the right outer side. Tier III uses distinct top/back
cells. [Port coordinates and usage](docs/USER_GUIDE.md) describe exact locations.

HomeCore exposes one generic device type, `homelink_furnace:industrial_furnace`,
with the tier as telemetry and standard `homecore:power` / `homecore:rename`
actions. Dashboard is optional and has no compile-time dependency. Real finished
operations publish ProductionReceipts with stable transaction IDs and the
recorded owner; no fictitious author is credited. Tasks does not need a patch.

ItemPort INPUT and OUTPUT plus coherent NeoForge item handlers allow external
logistics to feed and extract the machine. The Furnace does not push items,
route cargo or depend on Storage or Quarry. Optional Storage Pipes integration
checks use the actual adjacent Storage repository when available.

Optional JEI / REI development support uses `-PwithJei` / `-PwithRei`. Verification
of viewer behavior is recorded separately in [validation](docs/VALIDATION.md).

Build with the supplied wrapper from PowerShell:

```powershell
.\gradlew.bat build test verifyReleaseJar
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

Adjacent compatible HomeCore / HomeLinkEnergy checkouts are Gradle composites
by default. `-PuseLocalDependencies=false` selects explicitly declared published
dependencies; no hidden mavenLocal is used. `-Phomecore_dir=...` and
`-Penergy_dir=...` select local checkouts. Do not interpret a successful compile
as proof of gameplay behavior: executed unit tests, server GameTests, client
smoke evidence and remaining limits are listed in [VALIDATION](docs/VALIDATION.md).

Further documentation: [User guide](docs/USER_GUIDE.md), [HomeCore](docs/HOMECORE.md),
[energy](docs/ENERGY.md), [design](docs/DESIGN.md), [crafting](docs/RECIPES.md),
[implementation report in 20 points](docs/IMPLEMENTATION_REPORT.md),
[19 actual client screenshots](docs/screenshots/README.md).
