package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fluids.FluidStack;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;

import codechicken.nei.recipe.IRecipeHandler;
import gtnhintergalactic.recipe.GasSiphonRecipes;
import lombok.Setter;
import lombok.experimental.Accessors;

public class GasSiphonHandlerRecipe extends BaseHandlerRecipe {

    public GasSiphonHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler baseHandler) {
        List<GasSiphonRecipe> recipes = new ArrayList<>();
        for (Map.Entry<String, GasSiphonRecipes.GasSiphonRecipe> entry : GasSiphonRecipes.RECIPES.entrySet()) {
            for (Map.Entry<Integer, FluidStack> innerEntry : entry.getValue().depths.entrySet()) {
                recipes.add(
                    new GasSiphonRecipe().setFluid(innerEntry.getValue())
                        .setPlanet(entry.getKey())
                        .setDepth(innerEntry.getKey())
                        .setOutputAmount(innerEntry.getValue().amount)
                        .setBaseRatePerSecond((long) innerEntry.getValue().amount * 2)
                        .setEut(GasSiphonRecipes.calculateEUt(innerEntry.getKey(), entry.getValue().tier))
                        .setPlanetName(net.minecraft.util.StatCollector.translateToLocal(entry.getKey())));
            }
        }
        return recipes;
    }

    @Setter
    @Accessors(chain = true)
    private static class GasSiphonRecipe {

        private FluidStack fluid;
        private String planet;
        private int depth;
        private int outputAmount;
        // 注册表保存每10 tick产量；NEI基础速率使用L/s，即注册量的两倍。
        private long baseRatePerSecond;
        private int eut;
        private String planetName;
    }
}
