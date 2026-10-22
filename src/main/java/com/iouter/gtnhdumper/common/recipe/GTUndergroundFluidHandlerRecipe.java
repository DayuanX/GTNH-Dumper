package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraftforge.fluids.FluidRegistry;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.BaseRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeFluid;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.WorldgenRecipeUtil;

import codechicken.nei.recipe.IRecipeHandler;
import gtneioreplugin.util.DimensionHelper;
import gtneioreplugin.util.GT5UndergroundFluidHelper;
import gtneioreplugin.util.GT5UndergroundFluidHelper.UndergroundFluidWrapper;

public class GTUndergroundFluidHandlerRecipe extends BaseHandlerRecipe {

    public GTUndergroundFluidHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        List<UndergroundFluidRecipe> recipes = new ArrayList<>();
        Map<String, List<UndergroundFluidWrapper>> fluids = new TreeMap<>(GT5UndergroundFluidHelper.getAllEntries());
        for (Map.Entry<String, List<UndergroundFluidWrapper>> entry : fluids.entrySet()) {
            if (FluidRegistry.getFluid(entry.getKey()) == null) continue;
            List<UndergroundFluidWrapper> deposits = new ArrayList<>(entry.getValue());
            deposits.sort(
                Comparator
                    .comparingInt(
                        (UndergroundFluidWrapper deposit) -> DimensionHelper.getIndexByAbbr(deposit.dimension))
                    .thenComparing(deposit -> deposit.dimension)
                    .thenComparingInt(deposit -> deposit.minAmount)
                    .thenComparingInt(deposit -> deposit.maxAmount)
                    .thenComparingInt(deposit -> deposit.chance));
            for (UndergroundFluidWrapper deposit : deposits) {
                try {
                    ArrayList<RecipeFluid> outputFluids = new ArrayList<>();
                    // This identifies the fluid; deposit amounts and generation chance are metadata.
                    outputFluids.add(new RecipeFluid("fluid." + entry.getKey(), 0));
                    recipes.add(
                        new UndergroundFluidRecipe(
                            entry.getKey(),
                            deposit,
                            WorldgenRecipeUtil.getDimensionInputs(Collections.singletonList(deposit.dimension)),
                            outputFluids));

                } catch (RuntimeException | LinkageError e) {
                    if (!RecipeItemVariants.isCollecting()) throw e;
                    RecipeItemVariants.reportFailure("GTUndergroundFluidHandlerRecipe.java entry", e);
                }
            }
        }
        return recipes;
    }

    private static class UndergroundFluidRecipe extends BaseRecipe {

        private final String fluid;
        private final String dimension;
        private final int chance;
        private final int minAmount;
        private final int maxAmount;

        private UndergroundFluidRecipe(String fluid, UndergroundFluidWrapper deposit, ArrayList<Object> inputItems,
            ArrayList<RecipeFluid> outputFluids) {
            super(inputItems, null, null, outputFluids, null);
            this.fluid = fluid;
            dimension = deposit.dimension;
            chance = deposit.chance;
            minAmount = deposit.minAmount;
            maxAmount = deposit.maxAmount;
        }
    }
}
