# HomeCore contracts

Audited local versions: HomeCore 1.14.0, DashboardAPI.API_VERSION 1.9.0 and
HomeLink Energy 0.5.0. No Java dependency on Dashboard, Tasks, Quarry or Storage.

`FurnaceHomeCore` registers the BlockEntity provider and loaded masters. Devices
implement `DashboardDevice`, `NetworkMember`, `Switchable` and `Renamable`.
Identity, owner, name, power and network are persistent. A copied live device
identity is assigned a new UUID on registration, preventing registry collisions.
Network mutations use `DashboardAPI.bindDevice`; HomeCore validates network
management rights. Physical ownership / operator checks and HomeCore network
permissions control the menu; unknown owners do not gain implicit control.

Metrics: furnace_tier, furnace_status, enabled, temperature, active_jobs,
max_jobs, queued_items, pending_outputs, energy_stored, energy_capacity,
current_energy_draw, input_usage, output_usage, processed_total, xp_stored,
network_connected, redstone_mode. Item metrics use ITEM, percentages PERCENT,
energy HE and draw HE/t. `processed_total` counts operations as a dimensionless
LONG; a modded operation may produce several items. `network_connected` checks
that the recorded network exists and actually contains the device. There is one
device type for all tiers.

Actions: standard `homecore:power` (CONTROL) and `homecore:rename` (CONFIGURE).
HomeCore remains responsible for rename validation. Loaded off / redstone-blocked
machines report DISABLED. No-power, output-full, incomplete-structure and invalid
recipe report WARNING; only actually absent / removed / unloaded devices report
OFFLINE. Standard actions remain available on DISABLED / WARNING in this API.

Transition events: furnace_started, furnace_stopped, furnace_no_power,
furnace_power_restored, furnace_output_full, furnace_output_recovered,
furnace_recipe_invalid, furnace_recipe_restored. Warning entries have WARNING
severity; other transitions INFO. The initial load observes state without
replaying old events. Output, recipe and power warnings track their actual
conditions independently: toggling OFF does not falsely report recovered HE,
output space or a restored recipe. No per-item event is emitted.

An owner-attributed job records `ProductionStart` when its input is reserved.
When its real finished result exists, it publishes one `ProductionReceipt` with
stable transaction ID, actual recipe ID and quantity, owner, machine network,
start/completion server ticks and ProductionLog sequence. An unknown owner cooks
normally and emits no falsely attributed receipt. Crash retry, if any, retains
the transaction ID so consumers can deduplicate. The emitted flag is persisted.
XP and receipts are created at completion, including when output is full.
