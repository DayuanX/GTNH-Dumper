package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.List;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.BaseRecipe;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public class ShapedCraftingHandlerRecipe extends BaseHandlerRecipe {

    public ShapedCraftingHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    /** 标准九宫格有序合成；无序子类及改用专用坐标的机器交由其他适配器处理。 */
    public static boolean supports(Class<?> type) {
        if (ShapelessRecipeHandler.class.isAssignableFrom(type)) return false;
        String name = type.getName();
        // 物流焊接台有偏移网格和额外材料；Railcraft轧机沿用已有专用材料顺序。
        if (name.equals("logisticspipes.nei.NEISolderingStationRecipeManager") || type.getSimpleName()
            .equals("RecipeHandlerRollingMachineShaped")) return false;
        if (ShapedRecipeHandler.class.isAssignableFrom(type)) return true;
        // 这些已核对的九宫格处理器直接继承TemplateRecipeHandler。
        return name.equals("appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler")
            || name.equals("appeng.integration.modules.NEIHelpers.NEIFacadeRecipeHandler")
            || name.equals("mrtjp.projectred.core.libmc.recipe.PRShapedRecipeHandler");
    }

    /** 原版NEI九宫格原点(25,6)，格距18；非法坐标返回-1。 */
    public static int cellNumber(int x, int y) {
        int column = x - 25;
        int row = y - 6;
        if (column < 0 || column > 36 || row < 0 || row > 36 || column % 18 != 0 || row % 18 != 0) return -1;
        return 1 + column / 18 + 3 * (row / 18);
    }

    /** NEI材料携带坐标；逐条补齐九格，材料列表的遍历顺序不影响绑定。 */
    public static PositionedStack[] arrangeIngredients(List<PositionedStack> ingredients) {
        PositionedStack[] cells = new PositionedStack[9];
        for (PositionedStack stack : ingredients) {
            int serial = cellNumber(stack.relx, stack.rely);
            if (serial < 1 || serial > 9) {
                throw new IllegalArgumentException(
                    "Crafting ingredient outside 3x3 grid: " + stack.relx + "," + stack.rely);
            }
            if (cells[serial - 1] != null) {
                throw new IllegalArgumentException("Duplicate crafting cell: " + serial);
            }
            cells[serial - 1] = stack;
        }
        return cells;
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        List<BaseRecipe> recipes = new ArrayList<>();
        if (!(handler instanceof TemplateRecipeHandler recipeHandler)) {
            return null;
        }
        TemplateRecipeHandler loaded = (TemplateRecipeHandler) recipeHandler.getAllRecipeHandler();
        recipeHandler.arecipes.clear();
        recipeHandler.arecipes.addAll(loaded.arecipes);
        for (TemplateRecipeHandler.CachedRecipe recipe : recipeHandler.arecipes) {
            ArrayList<Object> inputItems = new ArrayList<>();
            for (PositionedStack stack : arrangeIngredients(recipe.getIngredients())) {
                inputItems.add(stack == null ? null : RecipeUtil.getRecipeItems(stack.items));
            }
            ArrayList<Object> otherItems = Utils.getRecipeItems(recipe.getOtherStacks());
            if (otherItems.isEmpty()) otherItems = null;
            BaseRecipe baseRecipe = new BaseRecipe(
                inputItems,
                null,
                Utils.getRecipeItems(recipe.getResult()),
                null,
                otherItems);
            recipes.add(baseRecipe);
        }
        return recipes;
    }
}
