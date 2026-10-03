package fr.lkdm.homelink.furnace.furnace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homelink.furnace.block.FurnaceLayout;
import fr.lkdm.homelink.furnace.block.FurnaceTier;
import fr.lkdm.homelink.furnace.config.FurnaceServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class FurnaceCoreTest {
    @Test void tierDefaultsAndUniqueRotatedFootprints() {
        int[] volumes = {1, 2, 8}, lanes = {2, 4, 8}, slots = {9, 18, 27};
        long[] energy = {2_000, 6_000, 16_000};
        for (FurnaceTier tier : FurnaceTier.values()) {
            assertEquals(volumes[tier.ordinal()], FurnaceLayout.cells(tier).size());
            assertEquals(lanes[tier.ordinal()], tier.jobs());
            assertEquals(slots[tier.ordinal()], tier.inputSlots());
            assertEquals(tier.inputSlots(), tier.outputSlots());
            assertEquals(energy[tier.ordinal()], tier.energyCapacity());
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                BlockPos origin = new BlockPos(19, 83, -4);
                HashSet<BlockPos> footprint = new HashSet<>();
                for (var cell : FurnaceLayout.cells(tier)) {
                    BlockPos position = FurnaceLayout.position(origin, facing, cell);
                    assertEquals(origin, FurnaceLayout.master(position, facing, cell));
                    assertEquals(cell, FurnaceLayout.locate(origin, facing, tier, position).orElseThrow());
                    footprint.add(position);
                }
                assertEquals(volumes[tier.ordinal()], footprint.size());
                assertEquals(Direction.UP, FurnaceLayout.input(tier, facing).face());
                assertEquals(facing.getOpposite(), FurnaceLayout.output(tier, facing).face());
                assertEquals(facing.getClockWise(), FurnaceLayout.energy(tier, facing).face());
            }
        }
    }
    @Test void schedulerIgnoresInvalidItemsAndIsRoundRobin() {
        FurnaceScheduler scheduler = new FurnaceScheduler();
        assertEquals(-1, scheduler.findInput(9, ignored -> false));
        assertEquals(1, scheduler.findInput(9, slot -> slot == 1 || slot == 7));
        assertEquals(7, scheduler.findInput(9, slot -> slot == 1 || slot == 7));
        assertEquals(1, scheduler.findInput(9, slot -> slot == 1 || slot == 7));
        assertArrayEquals(new int[]{0, 1, 2, 3}, scheduler.laneOrder(4));
        assertArrayEquals(new int[]{1, 2, 3, 0}, scheduler.laneOrder(4));
        assertArrayEquals(new int[]{2, 3, 0, 1}, scheduler.laneOrder(4));
        scheduler.restore(Integer.MAX_VALUE,Integer.MAX_VALUE);
        assertArrayEquals(new int[]{1,2,0},scheduler.laneOrder(3));
        assertEquals(1,scheduler.findInput(3,ignored->true));
    }
    @Test void statusAndRedstoneAreExplicit() {
        assertEquals(DeviceStatus.State.DISABLED, FurnaceStatus.SWITCHED_OFF.deviceState());
        assertEquals(DeviceStatus.State.DISABLED, FurnaceStatus.REDSTONE_PAUSED.deviceState());
        for (var status : new FurnaceStatus[]{FurnaceStatus.NO_POWER, FurnaceStatus.OUTPUT_FULL,
                FurnaceStatus.RECIPE_INVALID, FurnaceStatus.INCOMPLETE_STRUCTURE}) assertEquals(DeviceStatus.State.WARNING, status.deviceState());
        assertEquals(DeviceStatus.State.ONLINE, FurnaceStatus.PROCESSING.deviceState());
        assertTrue(RedstoneMode.IGNORE.allows(false)); assertTrue(RedstoneMode.IGNORE.allows(true));
        assertTrue(RedstoneMode.REQUIRE_SIGNAL.allows(true)); assertFalse(RedstoneMode.REQUIRE_SIGNAL.allows(false));
        assertTrue(RedstoneMode.REQUIRE_NO_SIGNAL.allows(false)); assertFalse(RedstoneMode.REQUIRE_NO_SIGNAL.allows(true));
        assertEquals(RedstoneMode.IGNORE, RedstoneMode.fromOrdinal(Integer.MAX_VALUE));
    }
    @Test void serverSettingsRejectUnsafeNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new FurnaceServerConfig.Settings(true, 8,
                Double.NaN, 100, 100, 200, 100, 20, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> new FurnaceServerConfig.Settings(true, 0,
                1, 100, 100, 200, 100, 20, 100, 100));
    }
}
