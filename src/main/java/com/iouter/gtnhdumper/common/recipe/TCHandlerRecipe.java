package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.glease.tc4tweak.api.infusionrecipe.EnhancedInfusionRecipe;
import net.glease.tc4tweak.api.infusionrecipe.InfusionRecipeExt;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;

import com.gtnewhorizons.aspectrecipeindex.nei.AlchemyRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.AspectCombinationHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.ShapedArcaneRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.ShapelessArcaneRecipeHandler;
import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.RecipeCoverage;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.recipe.IRecipeHandler;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;

public class TCHandlerRecipe extends BaseHandlerRecipe {

    public TCHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        List<TCRecipe> recipes = new ArrayList<>();
        if (handler instanceof AspectCombinationHandler) {
            for (Aspect aspect : Aspect.getCompoundAspects()) {
                Aspect[] components = aspect.getComponents();
                if (components == null) continue;
                AspectList inputAspects = new AspectList();
                for (Aspect iA : components) {
                    inputAspects.add(iA, 1);
                }
                AspectList outputAspects = new AspectList().add(aspect, 1);
                recipes.add(
                    new TCRecipe().withInputAspects(inputAspects)
                        .withOutputAspects(outputAspects));
            }
        } else if (handler.getClass() == ShapedArcaneRecipeHandler.class) {
            for (Object o : ThaumcraftApi.getCraftingRecipes()) {
                try {
                    if (!(o instanceof ShapedArcaneRecipe tcRecipe)) {
                        continue;
                    }
                    Object[] inputItems = new Object[9];
                    for (int i = 0; i < tcRecipe.getInput().length; i++) {
                        Object oInput = tcRecipe.getInput()[i];
                        if (isItem(oInput)) {
                            inputItems[(i / tcRecipe.width) * 3 + i % tcRecipe.width] = getItemFromObject(oInput);
                        }
                    }
                    recipes.add(
                        new TCRecipe().withInputItems(inputItems)
                            .withInputAspects(tcRecipe.aspects)
                            .withOutputItems(new RecipeItem(tcRecipe.getRecipeOutput()))
                            .withResearch(tcRecipe.getResearch()));

                } catch (RuntimeException | LinkageError e) {
                    if (!RecipeItemVariants.isCollecting()) throw e;
                    RecipeItemVariants.reportFailure("TCHandlerRecipe.java entry", e);
                }
            }
        } else if (handler.getClass() == ShapelessArcaneRecipeHandler.class) {
            for (Object o : ThaumcraftApi.getCraftingRecipes()) {
                try {
                    if (o instanceof ShapelessArcaneRecipe tcRecipe) {
                        recipes.add(
                            new TCRecipe().withInputItems(
                                Arrays.stream(
                                    tcRecipe.getInput()
                                        .toArray())
                                    .filter(TCHandlerRecipe::isItem)
                                    .map(TCHandlerRecipe::getItemFromObject)
                                    .toArray(Object[]::new))
                                .withInputAspects(tcRecipe.getAspects())
                                .withOutputItems(new RecipeItem(tcRecipe.getRecipeOutput()))
                                .withResearch(tcRecipe.getResearch()));
                    }

                } catch (RuntimeException | LinkageError e) {
                    if (!RecipeItemVariants.isCollecting()) throw e;
                    RecipeItemVariants.reportFailure("TCHandlerRecipe.java entry", e);
                }
            }
        } else if (handler instanceof AlchemyRecipeHandler) {
            for (Object o : ThaumcraftApi.getCraftingRecipes()) {
                try {
                    if (!(o instanceof CrucibleRecipe tcRecipe)) {
                        continue;
                    }
                    recipes.add(
                        new TCRecipe().withInputItems(getItemFromObject(tcRecipe.catalyst))
                            .withInputAspects(tcRecipe.aspects)
                            .withOutputItems(new RecipeItem(tcRecipe.getRecipeOutput()))
                            .withResearch(tcRecipe.key));

                } catch (RuntimeException | LinkageError e) {
                    if (!RecipeItemVariants.isCollecting()) throw e;
                    RecipeItemVariants.reportFailure("TCHandlerRecipe.java entry", e);
                }
            }
        } else if (handler.getClass() == InfusionRecipeHandler.class) {
            for (Object o : ThaumcraftApi.getCraftingRecipes()) {
                try {
                    if (!(o instanceof InfusionRecipe tcRecipe)) {
                        continue;
                    }
                    if (tcRecipe.getRecipeInput() == null) {
                        RecipeCoverage.unsupported(
                            tcRecipe.getClass()
                                .getName() + ":"
                                + tcRecipe.getResearch(),
                            "Infusion recipe has no static central item; dynamic inputs/outputs require a dedicated adapter");
                        continue;
                    }
                    try {
                        EnhancedInfusionRecipe r = InfusionRecipeExt.get()
                            .convert(tcRecipe);
                        ItemStack outputStack;
                        RecipeItem exampleInput = null;
                        String outputNbtPatch = null;
                        java.util.Map<String, Object> outputTransform = null;
                        ItemStack exampleStack = null;
                        if (com.iouter.gtnhdumper.common.utils.InfusionExamples.hasCustomOutput(tcRecipe)) {
                            exampleStack = com.iouter.gtnhdumper.common.utils.InfusionExamples.choose(
                                r.getCentral()
                                    .getRepresentativeStacks(),
                                codechicken.nei.ItemList.items,
                                candidate -> candidate.getItem() == tcRecipe.getRecipeInput()
                                    .getItem());
                            exampleStack = com.iouter.gtnhdumper.common.utils.InfusionExamples
                                .prepareCustomInput(tcRecipe, exampleStack);
                            exampleInput = new RecipeItem(exampleStack);
                            outputStack = (ItemStack) tcRecipe.getRecipeOutput(exampleStack);
                            outputTransform = com.iouter.gtnhdumper.common.utils.InfusionExamples.customRule(tcRecipe);
                        } else if (tcRecipe.getRecipeOutput() instanceof ItemStack) {
                            outputStack = (ItemStack) tcRecipe.getRecipeOutput();
                        } else {
                            Object[] obj = (Object[]) tcRecipe.getRecipeOutput();
                            NBTBase tag = (NBTBase) obj[1];
                            ItemStack example = com.iouter.gtnhdumper.common.utils.InfusionExamples.choose(
                                r.getCentral()
                                    .getRepresentativeStacks(),
                                codechicken.nei.ItemList.items,
                                candidate -> r.getCentral()
                                    .matches(candidate));
                            exampleInput = new RecipeItem(example);
                            outputStack = com.iouter.gtnhdumper.common.utils.InfusionExamples
                                .apply(example, (String) obj[0], tag);
                            net.minecraft.nbt.NBTTagCompound patch = new net.minecraft.nbt.NBTTagCompound();
                            patch.setTag((String) obj[0], tag.copy());
                            outputNbtPatch = patch.toString();
                        }
                        recipes.add(
                            new TCRecipe().withKeyItem(
                                getItemFromObject(
                                    r.getCentral()
                                        .getRepresentativeStacks()))
                                .withInputItems(
                                    r.getComponentsExt()
                                        .stream()
                                        .map(rI -> getItemFromObject(rI.getRepresentativeStacks()))
                                        .filter(java.util.Objects::nonNull)
                                        .toArray(Object[]::new))
                                .withInputAspects(
                                    exampleStack == null ? tcRecipe.getAspects() : tcRecipe.getAspects(exampleStack))
                                .withOutputItems(getItemFromObject(outputStack))
                                .withExample(exampleInput, outputNbtPatch)
                                .withOutputTransform(outputTransform)
                                .withInstability(
                                    exampleStack == null ? tcRecipe.getInstability()
                                        : tcRecipe.getInstability(exampleStack))
                                .withResearch(tcRecipe.getResearch()));
                    } catch (Exception e) {
                        RecipeItemVariants.reportFailure(
                            "Infusion " + tcRecipe.getClass()
                                .getName() + " research=" + tcRecipe.getResearch(),
                            e);
                    }

                } catch (RuntimeException | LinkageError e) {
                    if (!RecipeItemVariants.isCollecting()) throw e;
                    RecipeItemVariants.reportFailure("TCHandlerRecipe.java entry", e);
                }
            }
        }
        if (recipes.isEmpty()) {
            return null;
        }
        return recipes;
    }

    public static boolean isItem(Object o) {
        return (o instanceof ItemStack || o instanceof ItemStack[]
            || o instanceof String
            || (o instanceof List && !((List<?>) o).isEmpty()));
    }

    public static Object getItemFromObject(Object o) {
        ItemStack[] stacks = NEIServerUtils.extractRecipeItems(o);
        return RecipeUtil.getRecipeItems(stacks);
    }

    public static class TCRecipe {

        private Object keyItem;
        private RecipeItem exampleInput;
        private String outputNbtPatch;
        private java.util.Map<String, Object> outputTransform;

        public TCRecipe withOutputTransform(java.util.Map<String, Object> rule) {
            this.outputTransform = rule;
            return this;
        }

        /** outputItems为该输入的展示产物；keyItem仍保存完整中央输入条件。 */
        public TCRecipe withExample(RecipeItem input, String patch) {
            this.exampleInput = input;
            this.outputNbtPatch = patch;
            return this;
        }

        private Object[] inputItems;
        private AspectList inputAspects;

        private Object[] outputItems;
        private AspectList outputAspects;

        private java.util.List<String> research;

        private Integer instability;

        public TCRecipe() {}

        public TCRecipe withKeyItem(Object keyItem) {
            this.keyItem = keyItem;
            return this;
        }

        public TCRecipe withInputItems(Object... recipeItems) {
            this.inputItems = recipeItems;
            return this;
        }

        public TCRecipe withInputAspects(AspectList aspects) {
            this.inputAspects = aspects;
            return this;
        }

        public TCRecipe withOutputItems(Object... recipeItems) {
            this.outputItems = recipeItems;
            return this;
        }

        public TCRecipe withOutputAspects(AspectList aspects) {
            this.outputAspects = aspects;
            return this;
        }

        public TCRecipe withResearch(String research) {
            this.research = research == null || research.isEmpty() ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(research);
            return this;
        }

        public TCRecipe withInstability(int instability) {
            this.instability = instability;
            return this;
        }
    }
}
