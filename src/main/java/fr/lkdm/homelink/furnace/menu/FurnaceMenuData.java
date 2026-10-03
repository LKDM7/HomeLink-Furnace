package fr.lkdm.homelink.furnace.menu;

import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import net.minecraft.world.inventory.ContainerData;

/** Compact menu-only synchronization. Wide fields use two complete 32-bit words. */
public final class FurnaceMenuData implements ContainerData {
    public static final int COUNT = 29;
    private final IndustrialFurnaceBlockEntity furnace;
    public FurnaceMenuData(IndustrialFurnaceBlockEntity furnace) { this.furnace = furnace; }
    @Override public int get(int index) { return index >= 0 && index < COUNT ? furnace.menuValue(index) : 0; }
    @Override public void set(int index, int value) { }
    @Override public int getCount() { return COUNT; }
}
