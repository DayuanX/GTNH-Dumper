package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.emoniph.witchery.brewing.AltarPower;
import com.emoniph.witchery.brewing.WitcheryBrewRegistry;
import com.emoniph.witchery.brewing.action.BrewAction;
import com.emoniph.witchery.brewing.action.BrewActionRitualRecipe;
import com.emoniph.witchery.integration.NEICauldronRecipeHandler;
import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.BaseRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;

import codechicken.nei.recipe.IRecipeHandler;

public class WitcheryCauldronHandlerRecipe extends BaseHandlerRecipe {

    public WitcheryCauldronHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        if (!(handler instanceof NEICauldronRecipeHandler)) {
            return null;
        }

        List<CauldronRecipe> recipes = new ArrayList<>();
        for (BrewActionRitualRecipe ritual : WitcheryBrewRegistry.INSTANCE.getRecipes()) {
            try {
                for (BrewActionRitualRecipe.Recipe recipe : ritual.getExpandedRecipes()) {
                    try {
                        ArrayList<Object> inputItems = new ArrayList<>();
                        Arrays.stream(recipe.ingredients)
                            .filter(input -> input != null)
                            .map(RecipeItem::new)
                            .forEach(inputItems::add);

                        ArrayList<Object> outputItems = new ArrayList<>();
                        outputItems.add(new RecipeItem(recipe.result));

                        int altarPower = Arrays.stream(recipe.ingredients)
                            .filter(input -> input != null)
                            .map(WitcheryBrewRegistry.INSTANCE::getActionForItemStack)
                            .filter(action -> action != null)
                            .mapToInt(WitcheryCauldronHandlerRecipe::getAltarPower)
                            .sum();
                        recipes.add(new CauldronRecipe(inputItems, outputItems, altarPower));

                    } catch (RuntimeException | LinkageError e) {
                        if (!RecipeItemVariants.isCollecting()) throw e;
                        RecipeItemVariants.reportFailure("WitcheryCauldronHandlerRecipe.java entry", e);
                    }
                }

            } catch (RuntimeException | LinkageError e) {
                if (!RecipeItemVariants.isCollecting()) throw e;
                RecipeItemVariants.reportFailure("WitcheryCauldronHandlerRecipe.java entry", e);
            }
        }
        return recipes;
    }

    private static int getAltarPower(BrewAction action) {
        AltarPower altarPower = new AltarPower(0);
        action.accumulatePower(altarPower);
        return altarPower.getPower();
    }

    private static class CauldronRecipe extends BaseRecipe {

        private final int altarPower;

        private CauldronRecipe(ArrayList<Object> inputItems, ArrayList<Object> outputItems, int altarPower) {
            super(inputItems, null, outputItems, null, null);
            this.altarPower = altarPower;
        }
    }
}
