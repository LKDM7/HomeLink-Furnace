package fr.lkdm.homelink.furnace.compat.jei;

import fr.lkdm.homelink.furnace.HomeLinkFurnace;
import fr.lkdm.homelink.furnace.compat.ViewerInfo;
import mezz.jei.api.*;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class FurnaceJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime=value; }
    @Override public void onRuntimeUnavailable() { runtime=null; }
    public static IJeiRuntime runtime() { return runtime; }
    @Override public ResourceLocation getPluginUid() { return HomeLinkFurnace.id("jei"); }
    @Override public void registerRecipes(IRecipeRegistration registry) {
        ViewerInfo.pages().forEach((item, lines) -> registry.addItemStackInfo(new ItemStack(item), lines.toArray(Component[]::new)));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registry) {
        ViewerInfo.furnaces().forEach(item -> registry.addRecipeCatalyst(new ItemStack(item), RecipeTypes.SMELTING));
    }
}
