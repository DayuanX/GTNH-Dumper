package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.OreWorldgenRecipe;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.WorldgenRecipeUtil;

import codechicken.nei.recipe.IRecipeHandler;
import gregtech.api.enums.StoneType;
import gtneioreplugin.util.GT5OreLayerHelper;
import gtneioreplugin.util.GT5OreLayerHelper.OreLayerWrapper;

public class GTOreVeinHandlerRecipe extends BaseHandlerRecipe {

    public GTOreVeinHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        List<OreLayerWrapper> veins = new ArrayList<>(
            GT5OreLayerHelper.getOreVeinsByName()
                .values());
        veins.sort(Comparator.comparing(vein -> vein.veinName));
        List<OreVeinRecipe> recipes = new ArrayList<>();
        for (OreLayerWrapper vein : veins) {
            try {
                List<String> dims = WorldgenRecipeUtil.sortDimensions(vein.abbrDimNames);
                Set<StoneType> stoneTypes = WorldgenRecipeUtil.getStoneTypes(dims);
                ArrayList<Object> outputItems = new ArrayList<>();
                // Preserve all four slots, even if one layer has no available ore variants.
                for (int layer = 0; layer < vein.ores.length; layer++) {
                    outputItems.add(WorldgenRecipeUtil.getItemAlternatives(vein.getVeinLayerOre(layer, stoneTypes)));
                }
                recipes.add(new OreVeinRecipe(vein, dims, WorldgenRecipeUtil.getDimensionInputs(dims), outputItems));

            } catch (RuntimeException | LinkageError e) {
                if (!RecipeItemVariants.isCollecting()) throw e;
                RecipeItemVariants.reportFailure("GTOreVeinHandlerRecipe.java entry", e);
            }
        }
        return recipes;
    }

    private static class OreVeinRecipe extends OreWorldgenRecipe {

        private final String localizedName;
        private final String primary;
        private final String secondary;
        private final String between;
        private final String sporadic;
        private final int size;
        private final int density;
        private final int weight;
        private final Map<String, String> dimWorldGenHeightRange;

        private OreVeinRecipe(OreLayerWrapper vein, List<String> dims, ArrayList<Object> inputItems,
            ArrayList<Object> outputItems) {
            super(vein.veinName, vein.worldGenHeightRange, dims, inputItems, outputItems, null);
            localizedName = vein.getLocalizedName();
            primary = vein.mPrimaryVeinMaterial.getInternalName();
            secondary = vein.mSecondaryMaterial.getInternalName();
            between = vein.mBetweenMaterial.getInternalName();
            sporadic = vein.mSporadicMaterial.getInternalName();
            size = vein.size;
            density = vein.density;
            weight = vein.randomWeight;
            dimWorldGenHeightRange = new TreeMap<>(vein.dimWorldGenHeightRange);
        }
    }
}
