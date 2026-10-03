package fr.lkdm.homelink.furnace.furnace;

public enum FurnaceJobState {
    EMPTY, PROCESSING, PAUSED_NO_POWER, PAUSED_DISABLED, FINISHED_WAITING_OUTPUT;
    public static FurnaceJobState parse(String name) {
        try { return valueOf(name); } catch (IllegalArgumentException ignored) { return PAUSED_DISABLED; }
    }
}
