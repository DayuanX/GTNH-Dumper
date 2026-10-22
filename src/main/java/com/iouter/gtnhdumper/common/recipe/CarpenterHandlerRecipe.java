package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.BaseRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeFluid;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import forestry.factory.recipes.nei.NEIHandlerCarpenter;

public class CarpenterHandlerRecipe extends BaseHandlerRecipe {

    public CarpenterHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        if (!(handler instanceof NEIHandlerCarpenter carpenter)) {
            return null;
        }

        carpenter.loadCraftingRecipes(carpenter.getRecipeID(), (Object) null);
        List<BaseRecipe> recipes = new ArrayList<>();
        for (TemplateRecipeHandler.CachedRecipe cached : carpenter.arecipes) {
            if (!(cached instanceof NEIHandlerCarpenter.CachedCarpenterRecipe recipe)) {
                continue;
            }

            // Forestry keeps this input in a separate tank, outside getIngredients().
            ArrayList<RecipeFluid> inputFluids = null;
            if (recipe.tank != null && recipe.tank.tank != null) {
                FluidStack fluid = recipe.tank.tank.getFluid();
                if (fluid != null && fluid.getFluid() != null) {
                    inputFluids = new ArrayList<>();
                    inputFluids.add(new RecipeFluid(fluid).withNBT(fluid));
                }
            }
            // 木工机按列遍历且跳过空材料；固定映射到行优先九宫格，包装箱独立为I10。
            ArrayList<Object> inputs = new ArrayList<>(java.util.Collections.nCopies(10, null));
            for (codechicken.nei.PositionedStack stack : recipe.getIngredients()) {
                int index = stack.relx == 78 && stack.rely == 6 ? 9 : (stack.rely - 6) / 18 * 3 + (stack.relx - 5) / 18;
                if (index < 0 || index >= 10) throw new IllegalStateException("Unexpected carpenter input position");
                inputs.set(index, RecipeUtil.getRecipeItems(stack));
            }
            recipes.add(new BaseRecipe(inputs, inputFluids, Utils.getRecipeItems(recipe.getResult()), null, null));
        }
        return recipes;
    }
}
