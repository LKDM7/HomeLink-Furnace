package fr.lkdm.homelink.furnace.block;

/** Save-stable tiers. Slot counts and footprints are deliberately not configurable. */
public enum FurnaceTier {
    I(1, 1, 1, 2, 1.0, 9, 2_000, 100, 200, 100, 20),
    II(2, 1, 1, 4, 1.5, 18, 6_000, 160, 300, 300, 40),
    III(2, 2, 2, 8, 2.0, 27, 16_000, 240, 400, 800, 80);

    private final int width, height, depth, jobs, slots, warmupTicks, hotHoldTicks;
    private final double speed;
    private final long capacity, warmupEnergy, idleHeatEnergy;

    FurnaceTier(int width, int height, int depth, int jobs, double speed, int slots,
                long capacity, int warmupTicks, int hotHoldTicks, long warmupEnergy, long idleHeatEnergy) {
        this.width = width; this.height = height; this.depth = depth; this.jobs = jobs;
        this.speed = speed; this.slots = slots; this.capacity = capacity;
        this.warmupTicks = warmupTicks; this.hotHoldTicks = hotHoldTicks;
        this.warmupEnergy = warmupEnergy; this.idleHeatEnergy = idleHeatEnergy;
    }

    public String id() { return "industrial_furnace_" + number(); }
    public int number() { return ordinal() + 1; }
    public int width() { return width; }
    public int height() { return height; }
    public int depth() { return depth; }
    public int jobs() { return jobs; }
    public double speed() { return speed; }
    public int inputSlots() { return slots; }
    public int outputSlots() { return slots; }
    public long energyCapacity() { return capacity; }
    public int warmupTicks() { return warmupTicks; }
    public int hotHoldTicks() { return hotHoldTicks; }
    public long warmupEnergy() { return warmupEnergy; }
    public long idleHeatEnergyPerMinute() { return idleHeatEnergy; }
    public static FurnaceTier fromNumber(int number) { return values()[Math.max(0, Math.min(2, number - 1))]; }
}
