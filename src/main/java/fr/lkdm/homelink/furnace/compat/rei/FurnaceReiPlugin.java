package fr.lkdm.homelink.furnace.compat.rei;

import fr.lkdm.homelink.furnace.compat.ViewerInfo;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.minecraft.world.item.ItemStack;

@REIPluginClient
public final class FurnaceReiPlugin implements REIClientPlugin {
    @Override public void registerCategories(CategoryRegistry registry) {
        ViewerInfo.furnaces().forEach(item -> registry.addWorkstations(BuiltinPlugin.SMELTING, EntryStacks.of(item)));
    }
    @Override public void registerDisplays(DisplayRegistry registry) {
        ViewerInfo.pages().forEach((item, lines) -> registry.add(DefaultInformationDisplay
                .createFromEntry(EntryStacks.of(item), new ItemStack(item).getHoverName()).lines(lines)));
    }
}
