package fr.lkdm.homelink.furnace.furnace;

import static org.junit.jupiter.api.Assertions.*;
import fr.lkdm.homelink.furnace.block.FurnaceTier;
import fr.lkdm.homelink.furnace.config.FurnaceServerConfig;
import org.junit.jupiter.api.Test;

class FurnaceHeatTest {
    @Test void exactWarmupEnergyAndIdleCoolingPerTier() {
        for (FurnaceTier tier : FurnaceTier.values()) {
            var settings = FurnaceServerConfig.defaults(tier);
            FurnaceHeat heat = new FurnaceHeat();
            long remaining = settings.warmupEnergy();
            for (int t = 0; t < settings.warmupTicks(); t++) remaining -= heat.tick(settings, true, true, remaining);
            assertEquals(0, remaining);
            assertEquals(FurnaceHeat.State.READY, heat.state()); assertTrue(heat.canProcess());
            heat.tick(settings, true, true, 1); assertEquals(FurnaceHeat.State.PROCESSING, heat.state());
            for (int t = 0; t < settings.hotHoldTicks(); t++) heat.tick(settings, false, true, 0);
            assertTrue(heat.canProcess());
            heat.tick(settings, false, true, 0); assertEquals(FurnaceHeat.State.COOLING, heat.state());
            for (int t = 1; t < settings.warmupTicks(); t++) heat.tick(settings, false, true, 0);
            assertEquals(FurnaceHeat.State.COLD, heat.state()); assertFalse(heat.canProcess());
        }
    }
    @Test void noPowerCannotHeatAndSaveRestoresFractionalUpkeep() {
        var settings = FurnaceServerConfig.defaults(FurnaceTier.I);
        FurnaceHeat heat = new FurnaceHeat();
        for (int t = 0; t < 10_000; t++) assertEquals(0, heat.tick(settings, true, true, 0));
        assertEquals(0, heat.progress);
        for (int t = 0; t < 100; t++) heat.tick(settings, true, true, 1000);
        long paid = 0;
        for (int t = 0; t < 77; t++) paid += heat.tick(settings, true, false, 1000);
        FurnaceHeat loaded = new FurnaceHeat(); loaded.load(heat.save(), settings);
        assertEquals(heat.progress, loaded.progress); assertEquals(heat.hold, loaded.hold); assertEquals(heat.remainder, loaded.remainder);
        for (int t = 77; t < 200; t++) {
            assertEquals(heat.tick(settings, true, false, 1000), loaded.tick(settings, true, false, 1000));
        }
        assertEquals(1, paid);
    }
    @Test void longIdleUpkeepHasNoDriftAcrossRepeatedRestarts() {
        var base = FurnaceServerConfig.defaults(FurnaceTier.III);
        var settings = new FurnaceServerConfig.Settings(true, base.parallelJobs(), base.speedMultiplier(),
                base.energyBuffer(), base.warmupTicks(), 1_000_000, base.warmupEnergy(),
                base.idleHeatEnergyPerMinute(), base.baseEnergyPer200Ticks(), base.maxStoredXp());
        FurnaceHeat heat = new FurnaceHeat();
        for (int t = 0; t < settings.warmupTicks(); t++) heat.tick(settings, true, true, 100_000);
        long paid = 0;
        for (int t = 0; t < 120_000; t++) {
            paid += heat.tick(settings, true, false, 100_000);
            if (t % 173 == 0) {
                FurnaceHeat restarted = new FurnaceHeat(); restarted.load(heat.save(), settings); heat = restarted;
            }
        }
        assertEquals(8_000, paid); assertEquals(0, heat.remainder);
    }
    @Test void corruptHeatClampsAndFallsBackSafely() {
        var settings = FurnaceServerConfig.defaults(FurnaceTier.I);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("progress", -1); tag.putInt("hold", Integer.MAX_VALUE);
        tag.putLong("remainder", Long.MAX_VALUE); tag.putString("state", "IMPOSSIBLE");
        FurnaceHeat heat = new FurnaceHeat(); heat.load(tag, settings);
        assertEquals(0, heat.progress); assertEquals(FurnaceHeat.State.COLD, heat.state());
        assertEquals(settings.hotHoldTicks(), heat.hold); assertEquals(1_199, heat.remainder);
    }
}
