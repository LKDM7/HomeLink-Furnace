package fr.lkdm.homelink.furnace.config;

import java.util.EnumMap;
import fr.lkdm.homelink.furnace.block.FurnaceTier;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration. Dimensions and slot counts are save-format constants. */
public final class FurnaceServerConfig {
    public record Settings(boolean enabled, int parallelJobs, double speedMultiplier, long energyBuffer,
                           int warmupTicks, int hotHoldTicks, long warmupEnergy,
                           long idleHeatEnergyPerMinute, long baseEnergyPer200Ticks, double maxStoredXp) {
        public Settings {
            if (parallelJobs < 1 || parallelJobs > 8 || !Double.isFinite(speedMultiplier)
                    || speedMultiplier < .01 || speedMultiplier > 100 || energyBuffer < 1 || energyBuffer > 1_000_000_000L
                    || warmupTicks < 1 || warmupTicks > 1_000_000 || hotHoldTicks < 0 || hotHoldTicks > 1_000_000
                    || warmupEnergy < 1 || warmupEnergy > 1_000_000_000L || idleHeatEnergyPerMinute < 0
                    || idleHeatEnergyPerMinute > 1_000_000 || baseEnergyPer200Ticks < 1 || baseEnergyPer200Ticks > 1_000_000
                    || !Double.isFinite(maxStoredXp) || maxStoredXp < 0 || maxStoredXp > 1_000_000_000)
                throw new IllegalArgumentException("Unsafe furnace configuration");
        }
    }
    private record Values(ModConfigSpec.BooleanValue enabled, ModConfigSpec.IntValue jobs,
                          ModConfigSpec.DoubleValue speed, ModConfigSpec.LongValue buffer,
                          ModConfigSpec.IntValue warmup, ModConfigSpec.IntValue hold,
                          ModConfigSpec.LongValue heat, ModConfigSpec.LongValue idle) {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.LongValue BASE_ENERGY_PER_200_TICKS;
    public static final ModConfigSpec.DoubleValue MAX_STORED_XP;
    private static final EnumMap<FurnaceTier, Values> TIERS = new EnumMap<>(FurnaceTier.class);
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Global server switch; charging and extracting remain possible.").define("enabled", true);
        BASE_ENERGY_PER_200_TICKS = b.comment("HE per vanilla 200-tick operation; speed never changes this cost.")
                .defineInRange("baseEnergyPer200Ticks", 100L, 1, 1_000_000);
        MAX_STORED_XP = b.defineInRange("maxStoredXp", 1_000_000.0, 0, 1_000_000_000);
        for (FurnaceTier tier : FurnaceTier.values()) {
            b.push("tier" + tier.number());
            TIERS.put(tier, new Values(b.define("enabled", true),
                    b.defineInRange("parallelJobs", tier.jobs(), 1, tier.jobs()),
                    b.defineInRange("speedMultiplier", tier.speed(), .01, 100),
                    b.defineInRange("energyBuffer", tier.energyCapacity(), 1, 1_000_000_000),
                    b.defineInRange("warmupTicks", tier.warmupTicks(), 1, 1_000_000),
                    b.defineInRange("hotHoldTicks", tier.hotHoldTicks(), 0, 1_000_000),
                    b.defineInRange("warmupEnergy", tier.warmupEnergy(), 1, 1_000_000_000),
                    b.defineInRange("idleHeatEnergyPerMinute", tier.idleHeatEnergyPerMinute(), 0, 1_000_000)));
            b.pop();
        }
        SPEC = b.build();
    }
    private FurnaceServerConfig() {}
    public static Settings defaults(FurnaceTier tier) {
        return new Settings(true, tier.jobs(), tier.speed(), tier.energyCapacity(), tier.warmupTicks(),
                tier.hotHoldTicks(), tier.warmupEnergy(), tier.idleHeatEnergyPerMinute(), 100, 1_000_000);
    }
    public static Settings settings(FurnaceTier tier) {
        if (!SPEC.isLoaded()) return defaults(tier);
        Values v = TIERS.get(tier);
        return new Settings(ENABLED.get() && v.enabled.get(), v.jobs.get(), v.speed.get(), v.buffer.get(),
                v.warmup.get(), v.hold.get(), v.heat.get(), v.idle.get(), BASE_ENERGY_PER_200_TICKS.get(), MAX_STORED_XP.get());
    }
}
