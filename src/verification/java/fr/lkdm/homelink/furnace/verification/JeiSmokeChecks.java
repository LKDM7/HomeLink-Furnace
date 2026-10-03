package fr.lkdm.homelink.furnace.verification;

import fr.lkdm.homelink.furnace.compat.ViewerInfo;
import fr.lkdm.homelink.furnace.compat.jei.FurnaceJeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;

/** Loaded reflectively only when JEI is installed in the verification client. */
public final class JeiSmokeChecks {
    private JeiSmokeChecks() { }
    public static boolean ready() {
        var runtime=FurnaceJeiPlugin.runtime();
        if(runtime==null)return false;
        var manager=runtime.getRecipeManager();
        var catalysts=manager.createRecipeCatalystLookup(RecipeTypes.SMELTING).includeHidden().getItemStack().toList();
        var information=manager.createRecipeLookup(RecipeTypes.INFORMATION).includeHidden().get().toList();
        for(var furnace:ViewerInfo.furnaces()) {
            if(catalysts.stream().noneMatch(stack->stack.is(furnace)))throw new IllegalStateException("JEI smelting catalyst absent: "+furnace);
            if(information.stream().noneMatch(recipe->!recipe.getDescription().isEmpty()&&recipe.getIngredients().stream()
                    .anyMatch(ingredient->ingredient.getIngredient(VanillaTypes.ITEM_STACK).filter(stack->stack.is(furnace)).isPresent())))
                throw new IllegalStateException("JEI furnace information absent: "+furnace);
        }
        return true;
    }
}
