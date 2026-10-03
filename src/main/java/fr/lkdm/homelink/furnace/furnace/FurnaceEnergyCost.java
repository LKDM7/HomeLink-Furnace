package fr.lkdm.homelink.furnace.furnace;

/** Integer cumulative billing. Costs are bounded so all products remain within signed long. */
public final class FurnaceEnergyCost {
    public static final long DEFAULT_BASE = 100;
    public static final int REFERENCE_TICKS = 200;
    public static final int MAX_COOKING_TICKS = 1_000_000;
    public static final long MAX_OPERATION_ENERGY = 5_000_000_000L;
    private FurnaceEnergyCost() {}

    public static long operationEnergy(long base, int cookingTicks) {
        if (base < 1 || base > 1_000_000 || cookingTicks < 1 || cookingTicks > MAX_COOKING_TICKS)
            throw new IllegalArgumentException("Invalid recipe energy parameters");
        return Math.max(1, (base * cookingTicks + REFERENCE_TICKS / 2) / REFERENCE_TICKS);
    }
    public static int duration(int cookingTicks, double speed) {
        if (cookingTicks < 1 || cookingTicks > MAX_COOKING_TICKS || !Double.isFinite(speed) || speed < .01 || speed > 100)
            throw new IllegalArgumentException("Invalid recipe duration");
        return (int) Math.max(1, Math.ceil(cookingTicks / speed));
    }
    public static long cumulativeCost(long total, int duration, int progress) {
        if (total < 1 || total > MAX_OPERATION_ENERGY || duration < 1 || duration > 100_000_000
                || progress < 0 || progress > duration) throw new IllegalArgumentException("Invalid energy schedule");
        // Floor leaves the final positive debit for the completion tick. This allows an
        // exact operation-sized supply to finish while empty buffers still cannot advance.
        return total * progress / duration;
    }
    public static long nextCost(long total, int duration, int progress) {
        if (progress >= duration) return 0;
        return cumulativeCost(total, duration, progress + 1) - cumulativeCost(total, duration, progress);
    }
    /** Exact numerator remainder; persistence of progress is sufficient to preserve it. */
    public static long remainder(long total, int duration, int progress) { return total * progress % duration; }
}
