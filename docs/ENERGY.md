# HE accounting

Only HomeCore `EnergyApi.BLOCK` is registered. Its port is `CONSUMER`, `INPUT`;
insertion may charge the buffer, extraction always returns zero. Telemetry uses
canonical `EnergyApi.HE` and `EnergyApi.HE_PER_TICK` units.

For positive server SMELTING cooking time `c`:

```text
E = max(1, floor((baseEnergyPer200Ticks × c + 100) / 200))
d = max(1, ceil(c / speedMultiplier))
paid(p) = floor(E × p / d)
tickCost(p) = paid(p + 1) − paid(p)
```

`baseEnergyPer200Ticks` defaults to 100. The integer cumulative schedule pays
exactly E HE over d progressing ticks. The numerator remainder is carried
deterministically and the last positive debit occurs on completion, so an
exact operation-sized supply can finish. Even a tick whose integer debit is
zero requires positive stored HE; an empty buffer never advances a job.
Progress, paid energy and numerator remainder survive save/load.
Bounds on recipe time, energy and speed prevent overflow and non-finite values.

| Tier | Warm-up ticks | Warm-up HE | Hot hold ticks | Idle heat HE/min |
| --- | --- | --- | --- | --- |
| I | 100 | 100 | 200 | 20 |
| II | 160 | 300 | 300 | 40 |
| III | 240 | 800 | 400 | 80 |

Warm-up is independently billed with the same exact cumulative principle.
Idle heat uses an integer numerator carried between ticks (1,200 ticks/minute).
Power off, redstone block and absent energy pause jobs at their exact progress;
the thermal state cools with inactivity. Loading a chunk resumes tick simulation
without elapsed wall-clock catch-up. The machine remains chargeable while off.
