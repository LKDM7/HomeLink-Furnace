package fr.lkdm.homelink.furnace.furnace;

public enum RedstoneMode {
    IGNORE, REQUIRE_SIGNAL, REQUIRE_NO_SIGNAL;
    public boolean allows(boolean signal) { return this == IGNORE || (this == REQUIRE_SIGNAL) == signal; }
    public RedstoneMode next() { return values()[(ordinal() + 1) % values().length]; }
    public static RedstoneMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IGNORE;
    }
}
