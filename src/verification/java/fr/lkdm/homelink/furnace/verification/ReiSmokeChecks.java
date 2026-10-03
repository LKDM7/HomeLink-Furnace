package fr.lkdm.homelink.furnace.verification;

import fr.lkdm.homelink.furnace.compat.ViewerInfo;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.minecraft.world.item.ItemStack;

/** Loaded reflectively only when REI is installed in the verification client. */
public final class ReiSmokeChecks {
    private ReiSmokeChecks() { }
    public static boolean ready() {
        if(PluginManager.areAnyReloading())return false;
        var category=java.util.stream.StreamSupport.stream(CategoryRegistry.getInstance().spliterator(),false)
                .filter(value->value.getCategoryIdentifier().equals(BuiltinPlugin.SMELTING)).findFirst().orElse(null);
        if(category==null)return false;
        var workstations=category.getWorkstations().stream().flatMap(java.util.Collection::stream).toList();
        var information=DisplayRegistry.getInstance().get(BuiltinPlugin.INFO);
        for(var furnace:ViewerInfo.furnaces()) {
            if(workstations.stream().noneMatch(entry->entry.getValue() instanceof ItemStack stack&&stack.is(furnace)))return false;
            if(information.stream().noneMatch(display->display.getInputEntries().stream().flatMap(java.util.Collection::stream)
                    .anyMatch(entry->entry.getValue() instanceof ItemStack stack&&stack.is(furnace))))return false;
        }
        return true;
    }
}
