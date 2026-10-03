package fr.lkdm.homelink.furnace.furnace;

import fr.lkdm.homecore.api.device.DeviceStatus;

public enum FurnaceStatus {
    IDLE, READY, WARMING, PROCESSING, INPUT_EMPTY,
    SWITCHED_OFF, REDSTONE_PAUSED, NO_POWER, OUTPUT_FULL, INCOMPLETE_STRUCTURE, RECIPE_INVALID;
    public DeviceStatus.State deviceState() {
        return switch (this) {
            case SWITCHED_OFF, REDSTONE_PAUSED -> DeviceStatus.State.DISABLED;
            case NO_POWER, OUTPUT_FULL, INCOMPLETE_STRUCTURE, RECIPE_INVALID -> DeviceStatus.State.WARNING;
            default -> DeviceStatus.State.ONLINE;
        };
    }
    public String translationKey() { return "status.homelink_furnace." + name().toLowerCase(java.util.Locale.ROOT); }
}
