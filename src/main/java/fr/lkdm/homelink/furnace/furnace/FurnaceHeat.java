package fr.lkdm.homelink.furnace.furnace;

import fr.lkdm.homelink.furnace.config.FurnaceServerConfig.Settings;
import net.minecraft.nbt.CompoundTag;

/** Simple paid warmup, bounded hot hold, then linear cooling. No wall-clock catch-up. */
public final class FurnaceHeat {
    public enum State { COLD, WARMING, READY, PROCESSING, COOLING }
    public static final int TICKS_PER_MINUTE = 1_200;
    public int progress, hold;
    public long remainder;
    private State state = State.COLD;
    public State state() { return state; }
    public boolean canProcess() { return state == State.READY || state == State.PROCESSING; }
    public double temperature(Settings settings) { return 100.0 * Math.min(progress, settings.warmupTicks()) / settings.warmupTicks(); }

    /** The caller deducts returned HE before advancing jobs after the warmup completes. */
    public long tick(Settings settings, boolean working, boolean demand, long available) {
        progress = Math.min(progress, settings.warmupTicks());
        if (working && demand && available > 0) {
            if (progress >= settings.warmupTicks()) {
                hold = settings.hotHoldTicks(); state = State.PROCESSING; return 0;
            }
            long cost = FurnaceEnergyCost.nextCost(settings.warmupEnergy(), settings.warmupTicks(), progress);
            if (available >= cost) {
                progress++; hold = settings.hotHoldTicks();
                state = progress == settings.warmupTicks() ? State.READY : State.WARMING; return cost;
            }
        }
        if (progress == 0) { state = State.COLD; hold = 0; return 0; }
        long paid = 0;
        if (hold > 0) {
            if (working && progress == settings.warmupTicks() && available > 0) {
                long numerator = remainder + settings.idleHeatEnergyPerMinute();
                long due = numerator / TICKS_PER_MINUTE;
                if (available >= due) { remainder = numerator % TICKS_PER_MINUTE; paid = due; }
                else { hold = 0; }
            }
            if (hold > 0) { hold--; state = progress == settings.warmupTicks() ? State.READY : State.WARMING; return paid; }
        }
        progress--; state = progress == 0 ? State.COLD : State.COOLING; return paid;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("progress", progress); tag.putInt("hold", hold);
        tag.putLong("remainder", remainder); tag.putString("state", state.name()); return tag;
    }
    public void load(CompoundTag tag, Settings settings) {
        progress = Math.max(0, Math.min(settings.warmupTicks(), tag.getInt("progress")));
        hold = Math.max(0, Math.min(settings.hotHoldTicks(), tag.getInt("hold")));
        remainder = Math.max(0, Math.min(TICKS_PER_MINUTE - 1, tag.getLong("remainder")));
        try { state = State.valueOf(tag.getString("state")); } catch (IllegalArgumentException ignored) { state = State.COLD; }
        if (progress == 0) state = State.COLD;
        else if (progress < settings.warmupTicks() && (state == State.READY || state == State.PROCESSING)) state = State.WARMING;
    }
}
