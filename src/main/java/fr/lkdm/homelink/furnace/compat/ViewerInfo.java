package fr.lkdm.homelink.furnace.compat;

import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Shared localized information; optional viewers own their class loading. */
public final class ViewerInfo {
    private ViewerInfo() { }
    public static List<Item> furnaces() { return List.of(FurnaceRegistries.FURNACE_I_ITEM.get(), FurnaceRegistries.FURNACE_II_ITEM.get(), FurnaceRegistries.FURNACE_III_ITEM.get()); }
    public static Map<Item, List<Component>> pages() {
        Map<Item, List<Component>> result = new LinkedHashMap<>();
        for (int i = 0; i < furnaces().size(); i++) result.put(furnaces().get(i), List.of(
                Component.translatable("viewer.homelink_furnace.intro"), Component.translatable("viewer.homelink_furnace.tier." + (i + 1)),
                Component.translatable("gui.homelink_furnace.ports." + (i + 1)), Component.translatable("viewer.homelink_furnace.heat"),
                Component.translatable("viewer.homelink_furnace.xp"), Component.translatable("viewer.homelink_furnace.redstone")));
        return result;
    }
}
