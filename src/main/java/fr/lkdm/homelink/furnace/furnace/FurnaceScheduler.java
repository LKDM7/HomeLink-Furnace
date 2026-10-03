package fr.lkdm.homelink.furnace.furnace;

import java.util.function.IntPredicate;

/** Bounded scans triggered by inventory changes or a slow retry, never every idle tick. */
public final class FurnaceScheduler {
    private int nextInput, nextLane;
    public int findInput(int slots, IntPredicate valid) {
        if (slots <= 0) return -1;
        int start = Math.floorMod(nextInput, slots);
        for (int n = 0; n < slots; n++) {
            int slot = (start + n) % slots;
            if (valid.test(slot)) { nextInput = (slot + 1) % slots; return slot; }
        }
        nextInput = (start + 1) % slots;
        return -1;
    }
    public int[] laneOrder(int lanes) {
        int[] order = new int[Math.max(0, lanes)];
        int start = lanes > 0 ? Math.floorMod(nextLane, lanes) : 0;
        for (int n = 0; n < lanes; n++) order[n] = (start + n) % lanes;
        if (lanes > 0) nextLane = (start + 1) % lanes;
        return order;
    }
    public int inputCursor() { return nextInput; }
    public int laneCursor() { return nextLane; }
    public void restore(int input, int lane) { nextInput = Math.max(0, input); nextLane = Math.max(0, lane); }
}
