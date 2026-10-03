package fr.lkdm.homelink.furnace.homelink;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Unit;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.furnace.FurnaceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Public HomeCore device for every tier. Definitions are stable; values update on the server. */
public final class FurnaceDevice implements DashboardDevice, NetworkMember, Switchable, Renamable {
    public static final ResourceLocation TYPE = id("industrial_furnace");
    public static final Set<ResourceLocation> EVENTS = Set.of(id("furnace_started"), id("furnace_stopped"),
            id("furnace_no_power"), id("furnace_power_restored"), id("furnace_output_full"),
            id("furnace_output_recovered"), id("furnace_recipe_invalid"), id("furnace_recipe_restored"));
    private final IndustrialFurnaceBlockEntity furnace;
    private final UUID identity;
    private final Consumer<DeviceEvent> events;
    private final List<DeviceMetric<?>> metrics = new ArrayList<>();
    private final DeviceMetric<Integer> tier = integer("furnace_tier");
    private final DeviceMetric<FurnaceStatus> machineStatus = enumeration("furnace_status", FurnaceStatus.class, FurnaceStatus.IDLE);
    private final DeviceMetric<Boolean> enabled = bool("enabled");
    private final DeviceMetric<Percentage> temperature = percentage("temperature");
    private final DeviceMetric<Integer> active = integer("active_jobs");
    private final DeviceMetric<Integer> max = integer("max_jobs");
    private final DeviceMetric<Long> queue = count("queued_items", Unit.ITEM);
    private final DeviceMetric<Integer> pending = integer("pending_outputs");
    private final DeviceMetric<Long> stored = count("energy_stored", EnergyApi.HE);
    private final DeviceMetric<Long> capacity = count("energy_capacity", EnergyApi.HE);
    private final DeviceMetric<Long> draw = count("current_energy_draw", EnergyApi.HE_PER_TICK);
    private final DeviceMetric<Percentage> inputUsage = percentage("input_usage");
    private final DeviceMetric<Percentage> outputUsage = percentage("output_usage");
    private final DeviceMetric<Long> processed = count("processed_total", Unit.NONE);
    private final DeviceMetric<Double> xp = decimal("xp_stored");
    private final DeviceMetric<Boolean> connected = bool("network_connected");
    private final DeviceMetric<String> redstone = add(DeviceMetric.builder(id("redstone_mode"), name("redstone_mode"), MetricTypes.STRING, "IGNORE")
            .updatePolicy(UpdatePolicy.ON_CHANGE).build());
    private FurnaceStatus lastStatus;
    private boolean noPowerWarning, outputWarning, recipeWarning;

    public FurnaceDevice(IndustrialFurnaceBlockEntity furnace, Consumer<DeviceEvent> events) {
        this.furnace = furnace;
        this.identity = furnace.deviceId();
        this.events = events;
        refresh();
    }

    public boolean matches(IndustrialFurnaceBlockEntity candidate) { return furnace == candidate; }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("homelink_furnace", path); }
    private static Component name(String path) { return Component.translatable("metric.homelink_furnace." + path); }
    private <T> DeviceMetric<T> add(DeviceMetric<T> metric) { metrics.add(metric); return metric; }
    private DeviceMetric<Integer> integer(String path) {
        return add(DeviceMetric.builder(id(path), name(path), MetricTypes.INTEGER, 0).updatePolicy(UpdatePolicy.ON_CHANGE).build());
    }
    private DeviceMetric<Long> count(String path, Unit unit) {
        return add(DeviceMetric.builder(id(path), name(path), MetricTypes.LONG, 0L).unit(unit).updatePolicy(UpdatePolicy.ON_CHANGE).build());
    }
    private DeviceMetric<Double> decimal(String path) {
        return add(DeviceMetric.builder(id(path), name(path), MetricTypes.DOUBLE, 0.0).updatePolicy(UpdatePolicy.ON_CHANGE).build());
    }
    private DeviceMetric<Boolean> bool(String path) {
        return add(DeviceMetric.builder(id(path), name(path), MetricTypes.BOOLEAN, false).updatePolicy(UpdatePolicy.ON_CHANGE).build());
    }
    private DeviceMetric<Percentage> percentage(String path) {
        return add(DeviceMetric.builder(id(path), name(path), MetricTypes.PERCENTAGE, new Percentage(0)).unit(Unit.PERCENT)
                .range(0, 100, 0).updatePolicy(UpdatePolicy.ON_CHANGE).build());
    }
    private <E extends Enum<E>> DeviceMetric<E> enumeration(String path, Class<E> type, E initial) {
        return add(DeviceMetric.builder(id(path), name(path), MetricTypes.enumeration(id(path), type), initial)
                .updatePolicy(UpdatePolicy.ON_CHANGE).build());
    }

    public void refresh() {
        FurnaceStatus now = furnace.status();
        tier.setValue(furnace.tier().ordinal() + 1);
        machineStatus.setValue(now);
        enabled.setValue(furnace.enabled());
        temperature.setValue(new Percentage(Math.clamp(furnace.temperaturePercent(), 0, 100)));
        active.setValue(furnace.activeJobs());
        max.setValue(furnace.maxJobs());
        queue.setValue((long) furnace.queuedItems());
        pending.setValue(furnace.pendingOutputs());
        stored.setValue(furnace.energyStored());
        capacity.setValue(furnace.energyCapacity());
        draw.setValue(furnace.currentEnergyDraw());
        inputUsage.setValue(new Percentage(Math.clamp(furnace.inputUsage(), 0, 100)));
        outputUsage.setValue(new Percentage(Math.clamp(furnace.outputUsage(), 0, 100)));
        processed.setValue(furnace.processedTotal());
        xp.setValue((double) furnace.storedXp());
        connected.setValue(furnace.getLevel() instanceof ServerLevel level && furnace.homeNetworkId()
                .flatMap(network -> fr.lkdm.homecore.api.DashboardAPI.networks(level.getServer()).getNetwork(network))
                .filter(network -> network.devices().contains(identity)).isPresent());
        redstone.setValue(furnace.redstoneMode().name());
        boolean blockedOutput = furnace.pendingOutputs() > 0;
        boolean invalidRecipe = furnace.recipeInvalid();
        if (lastStatus == null) {
            noPowerWarning = now == FurnaceStatus.NO_POWER;
            outputWarning = blockedOutput;
            recipeWarning = invalidRecipe;
        } else if (isValid()) {
            if (now == FurnaceStatus.PROCESSING && lastStatus != FurnaceStatus.PROCESSING) publish("furnace_started", false);
            if (lastStatus == FurnaceStatus.PROCESSING && now != FurnaceStatus.PROCESSING) publish("furnace_stopped", false);
            if (now == FurnaceStatus.NO_POWER && !noPowerWarning) {
                noPowerWarning = true; publish("furnace_no_power", true);
            } else if (noPowerWarning && furnace.energyStored() > 0 && !furnace.powerWaiting()) {
                noPowerWarning = false; publish("furnace_power_restored", false);
            } else if (furnace.activeJobs() == 0 && furnace.energyStored() == 0) {
                // Cancelled work no longer waits for HE; cancellation is not a power restoration.
                noPowerWarning = false;
            }
            if (blockedOutput != outputWarning) publish(blockedOutput ? "furnace_output_full" : "furnace_output_recovered", blockedOutput);
            if (invalidRecipe != recipeWarning) publish(invalidRecipe ? "furnace_recipe_invalid" : "furnace_recipe_restored", invalidRecipe);
            outputWarning = blockedOutput;
            recipeWarning = invalidRecipe;
        }
        lastStatus = now;
    }
    private void publish(String type, boolean warning) {
        events.accept(new DeviceEvent(id(type), identity, Instant.now(), warning ? DeviceEvent.Severity.WARNING : DeviceEvent.Severity.INFO, Map.of()));
    }

    public static DeviceStatus mappedStatus(FurnaceStatus status) {
        return switch (status) {
            case SWITCHED_OFF, REDSTONE_PAUSED -> DeviceStatus.DISABLED;
            case NO_POWER, OUTPUT_FULL, INCOMPLETE_STRUCTURE, RECIPE_INVALID -> DeviceStatus.WARNING;
            default -> DeviceStatus.ONLINE;
        };
    }
    @Override public UUID id() { return identity; }
    @Override public ResourceLocation deviceType() { return TYPE; }
    @Override public Component displayName() { return furnace.displayName(); }
    @Override public boolean powered() { return furnace.enabled(); }
    @Override public ActionResult setPowered(boolean value) {
        if (!isValid()) return ActionResult.of(ActionResult.Code.DEVICE_OFFLINE);
        furnace.setEnabled(value); refresh(); return ActionResult.success();
    }
    @Override public ActionResult rename(String name) {
        if (!isValid()) return ActionResult.of(ActionResult.Code.DEVICE_OFFLINE);
        furnace.setCustomName(name); return ActionResult.success();
    }
    @Override public Optional<UUID> homeNetwork() { return furnace.homeNetworkId(); }
    @Override public Optional<UUID> owner() { return furnace.owner(); }
    @Override public boolean canConfigure(ServerPlayer player) { return FurnaceAccess.canConfigure(player, furnace); }
    @Override public void homeNetworkChanged(Optional<HomeNetwork> network) {
        network.ifPresentOrElse(value -> furnace.setHomeNetwork(value.id(), value.name()), furnace::clearHomeNetwork);
        refresh();
    }
    @Override public List<DeviceMetric<?>> metrics() { return List.copyOf(metrics); }
    @Override public Set<ResourceLocation> eventTypes() { return EVENTS; }
    @Override public Optional<BlockPos> position() { return Optional.of(furnace.getBlockPos().immutable()); }
    @Override public Optional<ResourceKey<Level>> dimension() { return Optional.ofNullable(furnace.getLevel()).map(Level::dimension); }
    @Override public DeviceStatus status() {
        return !isValid() ? DeviceStatus.OFFLINE : mappedStatus(furnace.status())
                .withMessage(Component.translatable("status.homelink_furnace." + furnace.status().name().toLowerCase(java.util.Locale.ROOT)));
    }
    @Override public boolean isValid() {
        return !furnace.isRemoved() && furnace.getLevel() instanceof ServerLevel level && level.isLoaded(furnace.getBlockPos())
                && level.getBlockEntity(furnace.getBlockPos()) == furnace && identity.equals(furnace.deviceId());
    }
}
