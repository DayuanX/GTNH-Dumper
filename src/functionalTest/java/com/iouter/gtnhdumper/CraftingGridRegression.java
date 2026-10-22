package com.iouter.gtnhdumper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.iouter.gtnhdumper.common.recipe.ShapedCraftingHandlerRecipe;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;

/** 九格绑定按坐标确定；覆盖空位、列表置换、非法坐标和无序子类。 */
public final class CraftingGridRegression {

    private CraftingGridRegression() {}

    public static void main(String[] args) {
        check(ShapedCraftingHandlerRecipe.supports(AdvancedHandler.class), "Inherited shaped handler");
        check(!ShapedCraftingHandlerRecipe.supports(ShapelessRecipeHandler.class), "Exclude shapeless subclass");
        List<PositionedStack> ingredients = new ArrayList<>(
            Arrays.asList(stack(61, 42, 1), stack(25, 6, 2), stack(43, 24, 3), stack(25, 24, 4)));
        PositionedStack[] expected = ShapedCraftingHandlerRecipe.arrangeIngredients(ingredients);
        check(
            expected[0] == ingredients.get(1) && expected[3] == ingredients.get(3)
                && expected[4] == ingredients.get(2)
                && expected[8] == ingredients.get(0),
            "Asymmetric positions");
        check(
            expected[1] == null && expected[2] == null
                && expected[5] == null
                && expected[6] == null
                && expected[7] == null,
            "Empty cells retained");
        for (int i = 0; i < 12; i++) {
            Collections.shuffle(ingredients, new java.util.Random(i));
            check(
                Arrays.equals(expected, ShapedCraftingHandlerRecipe.arrangeIngredients(ingredients)),
                "List order invariance");
        }
        check(
            ShapedCraftingHandlerRecipe.arrangeIngredients(Collections.singletonList(stack(43, 24, 5)))[4] != null,
            "Single ingredient stays in center");
        for (int x : new int[] { 25, 43, 61 }) {
            check(ShapedCraftingHandlerRecipe.cellNumber(x, 7) == -1, "Invalid Y must not alias a valid cell");
        }
        reject(Arrays.asList(stack(25, 6, 1), stack(25, 6, 2)));
        reject(Collections.singletonList(stack(26, 6, 1)));
        reject(Collections.singletonList(stack(43, 7, 1)));
        BannerPatternRegression.main(args);
        com.iouter.gtnhdumper.common.utils.AddonHandlerRegression.main(args);
        com.iouter.gtnhdumper.common.utils.StructureShapeRegression.main(args);
        System.out.println("Crafting grid regression passed");
    }

    private static PositionedStack stack(int x, int y, int metadata) {
        return new PositionedStack(new ItemStack(new Item(), 1, metadata), x, y, false);
    }

    private static void reject(List<PositionedStack> ingredients) {
        try {
            ShapedCraftingHandlerRecipe.arrangeIngredients(ingredients);
            throw new AssertionError("Invalid grid accepted");
        } catch (IllegalArgumentException expected) {
            // Invalid coordinates and duplicate cells must fail explicitly.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class AdvancedHandler extends ShapedRecipeHandler {
    }
}
