package fr.lkdm.homelink.furnace.furnace;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FurnaceEnergyCostTest {
    @Test void recipeReferenceAndDeterministicRounding() {
        assertEquals(100, FurnaceEnergyCost.operationEnergy(100, 200));
        assertEquals(200, FurnaceEnergyCost.operationEnergy(100, 400));
        assertEquals(1, FurnaceEnergyCost.operationEnergy(100, 1));
        assertEquals(2, FurnaceEnergyCost.operationEnergy(100, 3));
        assertEquals(1, FurnaceEnergyCost.operationEnergy(1, 1));
    }
    @Test void speedChangesOnlyDuration() {
        assertEquals(200, FurnaceEnergyCost.duration(200, 1));
        assertEquals(134, FurnaceEnergyCost.duration(200, 1.5));
        assertEquals(100, FurnaceEnergyCost.duration(200, 2));
        for (double speed : new double[]{.01, 1, 1.5, 2, 100}) {
            int duration = FurnaceEnergyCost.duration(200, speed);
            long paid = 0;
            for (int progress = 0; progress < duration; progress++) paid += FurnaceEnergyCost.nextCost(100, duration, progress);
            assertEquals(100, paid);
            assertTrue(FurnaceEnergyCost.nextCost(100, duration, duration - 1) >= 1);
        }
    }
    @Test void longSeriesAndFractionalRecipesNeverDrift() {
        long paid = 0, expected = 0;
        for (int operation = 0; operation < 10_000; operation++) {
            int cooking = 1 + operation % 999;
            long cost = FurnaceEnergyCost.operationEnergy(100, cooking);
            int duration = FurnaceEnergyCost.duration(cooking, 1.5);
            for (int p = 0; p < duration; p++) paid += FurnaceEnergyCost.nextCost(cost, duration, p);
            expected += cost;
        }
        assertEquals(expected, paid);
    }
    @Test void rejectsOverflowAndNonfiniteInputs() {
        assertThrows(IllegalArgumentException.class, () -> FurnaceEnergyCost.duration(200, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> FurnaceEnergyCost.duration(200, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> FurnaceEnergyCost.duration(0, 1));
        assertThrows(IllegalArgumentException.class, () -> FurnaceEnergyCost.operationEnergy(Long.MAX_VALUE, 200));
        assertThrows(IllegalArgumentException.class, () -> FurnaceEnergyCost.cumulativeCost(Long.MAX_VALUE, 1, 1));
    }
}
