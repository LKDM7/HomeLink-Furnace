package fr.lkdm.homelink.furnace.furnace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import fr.lkdm.homecore.api.production.ProductionStart;
import fr.lkdm.homelink.furnace.block.FurnaceTier;
import fr.lkdm.homelink.furnace.config.FurnaceServerConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class FurnaceJobTest {
    private FurnaceJob job(int duration, long energy) {
        return new FurnaceJob(UUID.randomUUID(), ResourceLocation.parse("minecraft:iron_ingot_from_smelting_raw_iron"),
                new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT), UUID.randomUUID(),
                new ProductionStart(UUID.randomUUID(), 91), 240, 200, duration, energy, .7f);
    }
    @Test void exactlyOneOperationBudgetFinishesForEverySpeed() {
        for (double speed : new double[]{.01, 1, 1.5, 2, 100}) {
            FurnaceJob job = job(FurnaceEnergyCost.duration(200, speed), 100);
            long buffer = 100;
            for (int t = 0; t < job.duration; t++) buffer -= job.advance(buffer);
            assertEquals(0, buffer); assertTrue(job.completed()); assertEquals(100, job.paidEnergy);
            assertTrue(job.finish()); assertFalse(job.finish());
            assertEquals(1, job.pending.getCount()); assertEquals(FurnaceJobState.FINISHED_WAITING_OUTPUT, job.state);
        }
    }
    @Test void zeroEnergyCannotAdvanceIncludingFractionalTicks() {
        FurnaceJob job = job(200, 100);
        for (int t = 0; t < 100_000; t++) assertEquals(0, job.advance(0));
        assertEquals(0, job.progress); assertEquals(0, job.paidEnergy);
        assertEquals(FurnaceJobState.PAUSED_NO_POWER, job.state);
        long paid = job.advance(100);
        assertEquals(1, job.progress);
        int progress = job.progress;
        job.advance(0); assertEquals(progress, job.progress);
        assertEquals(paid, job.paidEnergy);
        assertFalse(job.finish()); assertTrue(job.pending.isEmpty());
    }
    @Test void reservedInputIsOwnedAndNotAliased() {
        ItemStack raw = new ItemStack(Items.RAW_IRON);
        FurnaceJob job = new FurnaceJob(UUID.randomUUID(), ResourceLocation.parse("test:smelting"), raw,
                new ItemStack(Items.IRON_INGOT), null, null, 0, 200, 200, 100, .7f);
        raw.setCount(0); assertEquals(1, job.reservedInput.getCount()); assertNull(job.producer);
    }
    @Test void nbtRoundTripPreservesTransactionReservationProgressAndProductionStart() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        FurnaceJob original = job(134, 100);
        for (int t = 0; t < 57; t++) original.advance(1000);
        FurnaceJob loaded = FurnaceJob.load(original.save(registries), registries).orElseThrow();
        assertEquals(original.transactionId, loaded.transactionId); assertEquals(original.recipeId, loaded.recipeId);
        assertEquals(original.producer, loaded.producer); assertEquals(original.start, loaded.start);
        assertEquals(original.startedTick, loaded.startedTick); assertEquals(original.progress, loaded.progress);
        assertEquals(original.paidEnergy, loaded.paidEnergy); assertEquals(original.energyRemainder(), loaded.energyRemainder());
        assertTrue(ItemStack.matches(original.reservedInput, loaded.reservedInput));
        assertTrue(ItemStack.matches(original.expectedResult, loaded.expectedResult));
        for (int t = loaded.progress; t < loaded.duration; t++) loaded.advance(1000);
        assertTrue(loaded.finish()); loaded.receiptPublished = true; loaded.completedTick = 900;
        FurnaceJob pending = FurnaceJob.load(loaded.save(registries), registries).orElseThrow();
        assertTrue(ItemStack.matches(loaded.pending, pending.pending)); assertTrue(pending.receiptPublished);
        assertEquals(900, pending.completedTick); assertEquals(FurnaceJobState.FINISHED_WAITING_OUTPUT, pending.state);
        assertFalse(pending.finish());
    }
    @Test void corruptPaidEnergyOrPendingResultCannotBecomeFreeOutput() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        FurnaceJob job = job(200, 100); job.advance(1000); job.advance(1000);
        var tag = job.save(registries); tag.putLong("paid", 0);
        assertTrue(FurnaceJob.load(tag, registries).isEmpty());
        tag = job.save(registries); tag.put("pending", new ItemStack(Items.DIAMOND).save(registries));
        assertTrue(FurnaceJob.load(tag, registries).isEmpty());
    }
    @Test void parallelJobsAndWarmupFinishWithExactlyTheCombinedBudget() {
        for(FurnaceTier tier:FurnaceTier.values()){
            var settings=FurnaceServerConfig.defaults(tier);var heat=new FurnaceHeat();
            var scheduler=new FurnaceScheduler();var jobs=new FurnaceJob[tier.jobs()];
            for(int lane=0;lane<jobs.length;lane++)jobs[lane]=job(FurnaceEnergyCost.duration(200,tier.speed()),100);
            long buffer=tier.warmupEnergy()+100L*tier.jobs();
            int finished=0;
            for(int t=0;t<2_000&&finished<jobs.length;t++){
                buffer-=heat.tick(settings,true,true,buffer);
                if(heat.canProcess())for(int lane:scheduler.laneOrder(jobs.length)){
                    var job=jobs[lane];if(job.completed())continue;
                    buffer-=job.advance(buffer);if(job.finish())finished++;
                }
            }
            assertEquals(jobs.length,finished,tier.name());assertEquals(0,buffer,tier.name());
            for(var job:jobs){assertEquals(100,job.paidEnergy);assertEquals(1,job.pending.getCount());}
        }
    }
    @Test void powerCutAndNbtRestartFreezeExactProgressAndPaidFraction() {
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var job=job(134,100);long buffer=100;
        for(int t=0;t<47;t++)buffer-=job.advance(buffer);
        int progress=job.progress;long paid=job.paidEnergy,remainder=job.energyRemainder();
        for(int t=0;t<10_000;t++)job.advance(0);
        job=FurnaceJob.load(job.save(registries),registries).orElseThrow();
        assertEquals(progress,job.progress);assertEquals(paid,job.paidEnergy);assertEquals(remainder,job.energyRemainder());
        while(!job.completed())buffer-=job.advance(buffer);
        assertEquals(0,buffer);assertTrue(job.finish());
    }
}
