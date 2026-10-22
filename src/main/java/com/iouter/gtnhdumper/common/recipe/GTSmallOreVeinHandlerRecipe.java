package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.OreWorldgenRecipe;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.WorldgenRecipeUtil;

import codechicken.nei.recipe.IRecipeHandler;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.common.ores.IOreAdapter;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;
import gtneioreplugin.util.GT5OreSmallHelper;
import gtneioreplugin.util.GT5OreSmallHelper.OreSmallWrapper;

public class GTSmallOreVeinHandlerRecipe extends BaseHandlerRecipe {

    public GTSmallOreVeinHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        List<OreSmallWrapper> veins = new ArrayList<>(GT5OreSmallHelper.SMALL_ORES_BY_NAME.values());
        veins.sort(Comparator.comparing(vein -> vein.oreGenName));
        List<SmallOreRecipe> recipes = new ArrayList<>();
        for (OreSmallWrapper vein : veins) {
            try {
                List<String> dims = WorldgenRecipeUtil.sortDimensions(vein.enabledDims);
                List<ItemStack> ores = new ArrayList<>();
                List<ItemStack> dusts = new ArrayList<>();
                String localizedName;
                try (OreInfo<IOreMaterial> info = OreInfo.getNewInfo()) {
                    info.material = vein.material;
                    info.isSmall = true;
                    localizedName = OreManager.getLocalizedName(info);
                    collectVariants(info, WorldgenRecipeUtil.getStoneTypes(dims), ores, dusts);
                    if (ores.isEmpty()) {
                        // Match NEI's fallback when no enabled dimension provides a supported stone.
                        collectVariants(info, StoneType.STONE_TYPES, ores, dusts);
                    }
                }

                ArrayList<Object> outputItems = new ArrayList<>();
                WorldgenRecipeUtil.addItemAlternatives(outputItems, ores);
                ArrayList<Object> otherItems = new ArrayList<>();
                List<ItemStack> drops = GT5OreSmallHelper.ORE_MAT_TO_DROPS.get(vein.material);
                if (drops != null && !drops.isEmpty()) {
                    // NEI substitutes stone-specific dusts for the first potential drop.
                    WorldgenRecipeUtil.addItemAlternatives(
                        otherItems,
                        dusts.isEmpty() ? Collections.singletonList(drops.get(0)) : dusts);
                    for (int i = 1; i < drops.size(); i++) {
                        WorldgenRecipeUtil.addItemAlternatives(otherItems, Collections.singletonList(drops.get(i)));
                    }
                }
                recipes.add(
                    new SmallOreRecipe(
                        vein,
                        localizedName,
                        dims,
                        WorldgenRecipeUtil.getDimensionInputs(dims),
                        outputItems,
                        otherItems));

            } catch (RuntimeException | LinkageError e) {
                if (!RecipeItemVariants.isCollecting()) throw e;
                RecipeItemVariants.reportFailure("GTSmallOreVeinHandlerRecipe.java entry", e);
            }
        }
        return recipes;
    }

    private static void collectVariants(OreInfo<IOreMaterial> info, Iterable<StoneType> stoneTypes,
        List<ItemStack> ores, List<ItemStack> dusts) {
        info.stoneType = null;
        IOreAdapter<?> adapter = OreManager.getAdapter(info);
        if (adapter == null) return;
        for (StoneType stoneType : stoneTypes) {
            info.stoneType = stoneType;
            if (!adapter.supports(info)) continue;
            ItemStack ore = adapter.getStack(info, 1);
            if (ore != null) {
                ores.add(ore);
                ItemStack dust = stoneType.getDust(true, 1);
                if (dust != null) dusts.add(dust);
            }
        }
    }

    private static class SmallOreRecipe extends OreWorldgenRecipe {

        private final String localizedName;
        private final String material;
        private final int amountPerChunk;

        private SmallOreRecipe(OreSmallWrapper vein, String localizedName, List<String> dims,
            ArrayList<Object> inputItems, ArrayList<Object> outputItems, ArrayList<Object> otherItems) {
            super(vein.oreGenName, vein.worldGenHeightRange, dims, inputItems, outputItems, otherItems);
            this.localizedName = localizedName;
            material = vein.material.getInternalName();
            amountPerChunk = vein.amountPerChunk;
        }
    }
}
