package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.cropsnh.api.IBreedingRequirement;
import com.gtnewhorizon.cropsnh.api.ICropCard;
import com.gtnewhorizon.cropsnh.api.ICropMutation;
import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.common.recipe.GTDefaultHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeFluid;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;

import codechicken.nei.NEIServerUtils;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.IRecipeMetadataStorage;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;

public class Transformer {

    private Transformer() {}

    public static GTDefaultHandlerRecipe.GTDumpedRecipe transformGTRecipe(GTRecipe src) {
        if (src == null) {
            return null;
        }
        if (src.mHidden) {
            return null;
        }
        // 未注册输入无法在游戏物品库中取得；整方排除，不能删除输入后留下免费产物。
        if (src.mInputs != null) for (ItemStack input : src.mInputs) {
            if (input != null && net.minecraft.item.Item.itemRegistry.getNameForObject(input.getItem()) == null) {
                String identity = input.getItem()
                    .getClass()
                    .getName() + ":"
                    + codechicken.lib.inventory.InventoryUtils.actualDamage(input);
                RecipeCoverage.unsupported(
                    "unregisteredGTInput:" + src.getRecipeCategory().recipeMap.unlocalizedName + ":" + identity,
                    "Excluded whole recipe: input has no registry name; item=" + identity
                        + "; unlocalizedName="
                        + input.getItem()
                            .getUnlocalizedName()
                        + "; nbt="
                        + Utils.getItemNBT(input));
                return null;
            }
        }
        // Keep the original concrete inputs too, before GT expands/unifies alternatives.
        if (src.mInputs != null) for (ItemStack stack : src.mInputs) RecipeItemVariants.capture(stack);
        if (src.mOutputs != null) for (ItemStack stack : src.mOutputs) RecipeItemVariants.captureExactOutput(stack);
        if (src.mSpecialItems instanceof ItemStack stack) RecipeItemVariants.capture(stack);
        if (src.mSpecialItems instanceof ItemStack[]stacks) {
            for (ItemStack stack : stacks) RecipeItemVariants.capture(stack);
        }
        ArrayList<Object> inputItems = getInputItems(src);
        ArrayList<RecipeFluid> inputFluids = getInputFluids(src);
        ArrayList<Object> outputItems = getOutputItems(src);
        ArrayList<RecipeFluid> outputFluids = getOutputFluids(src);
        ArrayList<Object> otherItems = new ArrayList<>();
        if (src.mSpecialItems instanceof ItemStack temp) {
            otherItems.add(new RecipeItem(temp));
        } else if (src.mSpecialItems instanceof ItemStack[]stacks) {
            // GT特殊槽的数组是同一槽位的候选物品；装配线的研究闪存也使用此形式。
            Object candidates = RecipeUtil.getRecipeItems(stacks);
            if (candidates != null) otherItems.add(candidates);
        }
        if (otherItems.isEmpty()) otherItems = null;
        IRecipeMetadataStorage metadataStorage = src.getMetadataStorage();
        Map<String, Object> metadata = new LinkedHashMap<>();
        try {
            Class<?> clazz = RecipeMetadataKey.class;
            Field idField = clazz.getDeclaredField("identifier");
            idField.setAccessible(true);
            for (Map.Entry<RecipeMetadataKey<?>, Object> meta : metadataStorage.getEntries()) {
                try {
                    Object value = meta.getValue();
                    String key = idField.get(meta.getKey())
                        .toString();
                    metadata.put(key, CommonProxy.isCropsNHLoaded ? cropMetadata(value) : value);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            metadata = null;
        }
        // 与GT燃料格式器一致：特殊值乘流体量或容器容量，非流体回退1000。
        if (src.getRecipeCategory().recipeMap.getFrontend()
            .getNEIProperties().neiSpecialInfoFormatter instanceof gregtech.nei.formatter.FuelSpecialValueFormatter) {
            int amount = src.mFluidInputs.length > 0 ? src.mFluidInputs[0].amount : 0;
            if (amount == 0) for (ItemStack input : src.mInputs) {
                amount = net.minecraftforge.fluids.FluidContainerRegistry.getContainerCapacity(input);
                if (amount > 0) break;
            }
            if (amount == 0) amount = 1000;
            if (metadata == null) metadata = new LinkedHashMap<>();
            metadata.put("fuelEnergyEU", (long) src.mSpecialValue * amount);
        }
        if (metadata != null && metadata.isEmpty()) metadata = null;
        thaumcraft.api.aspects.AspectList inputAspects = addonInputAspects(src, metadata);
        // These named pools use the special slot only as an essentia display, not a selectable item input.
        if (inputAspects != null) otherItems = null;
        return new GTDefaultHandlerRecipe.GTDumpedRecipe(
            inputItems,
            inputFluids,
            outputItems,
            outputFluids,
            otherItems,
            src.mEUt,
            src.mDuration,
            src.mSpecialValue,
            metadata).withSourceMod(GTDefaultHandlerRecipe.originalOwner(src))
                .withInputAspects(inputAspects)
                .withDisplayData(src);
    }

    /** 工业注魔与炼金的源质需求沿用神秘时代的 inputAspects 结构；其他特殊槽保持原有语义。 */
    private static thaumcraft.api.aspects.AspectList addonInputAspects(GTRecipe recipe, Map<String, Object> metadata) {
        String pool = recipe.getRecipeCategory().recipeMap.unlocalizedName;
        String key = switch (pool) {
            case "gtnl.recipe.industrial_infusion_crafting" -> "gtnl_infusion_aspects";
            case "gtnl.recipe.industrial_shaped_arcane_crafting" -> "gtnl_arcane_vis";
            default -> null;
        };
        if (key != null) {
            if (metadata == null || !(metadata.get(key) instanceof thaumcraft.api.aspects.AspectList aspects))
                throw new IllegalStateException("Missing essentia metadata for " + pool);
            return aspects;
        }
        if (pool.equals("tst.common.recipe.IndustrialMagicMatrixRecipeMap.name")
            || pool.equals("tst.common.recipe.IndustrialAlchemyTowerRecipeMap.name")) {
            if (!(recipe.mSpecialItems instanceof ItemStack stack) || !stack.hasTagCompound())
                throw new IllegalStateException("Missing essentia display stack for " + pool);
            thaumcraft.api.aspects.AspectList aspects = new thaumcraft.api.aspects.AspectList();
            aspects.readFromNBT(stack.getTagCompound());
            return aspects;
        }
        return null;
    }

    /** CropsNH metadata stores live registry objects; export identities and breeding rules, not runtime state. */
    private static Object cropMetadata(Object value) {
        if (value instanceof ICropCard crop) return crop.getId();
        if (value instanceof ICropMutation mutation) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(
                "output",
                mutation.getOutput()
                    .getId());
            data.put(
                "parents",
                mutation.getParents()
                    .stream()
                    .map(ICropCard::getId)
                    .toArray(String[]::new));
            data.put(
                "requirements",
                mutation.getRequirements()
                    .stream()
                    .map(IBreedingRequirement::getDescription)
                    .toArray(String[]::new));
            data.put(
                "subSoils",
                RecipeUtil.getRecipeItems(
                    mutation.getSubSoilsForNEI(false)
                        .toArray(new ItemStack[0])));
            data.put(
                "catalysts",
                mutation.getBreedingMachineCatalystsForNEI(false)
                    .stream()
                    .map(stacks -> RecipeUtil.getRecipeItems(stacks.toArray(new ItemStack[0])))
                    .toArray());
            return data;
        }
        return value;
    }

    public static ArrayList<Object> getInputItems(GTRecipe gtRecipe) {
        ArrayList<Object> inputItems = new ArrayList<>();
        for (int i = 0; i < gtRecipe.mInputs.length; i++) {
            int chance = gtRecipe.getInputChance(i);
            if (gtRecipe instanceof GTRecipe.GTRecipe_WithAlt gtRecipeWithAlt) {
                Object stackObj = gtRecipeWithAlt.getAltRepresentativeInput(i);
                if (stackObj instanceof ItemStack stackAlt) {
                    inputItems.add(new RecipeItem(stackAlt).withChance(chance));
                } else if (stackObj instanceof ItemStack[]stacks) {
                    inputItems.add(RecipeUtil.getRecipeItems(stacks, chance));
                }
            } else {
                ItemStack stack = gtRecipe.mInputs[i];
                if (stack == null) continue;
                inputItems.add(
                    RecipeUtil.getRecipeItems(
                        NEIServerUtils.extractRecipeItems(GTOreDictUnificator.getNonUnifiedStacks(stack)),
                        chance));
            }
        }
        if (inputItems.isEmpty()) return null;
        return inputItems;
    }

    public static ArrayList<Object> getOutputItems(GTRecipe gtRecipe) {
        ArrayList<Object> outputItems = new ArrayList<>();
        ItemStack[] stacks = gtRecipe.getRecipeCategory().recipeMap.getFrontend()
            .getNEIProperties().itemOutputsGetter.apply(gtRecipe);
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) outputItems.add(new RecipeItem(stacks[i]).withChance(gtRecipe.getOutputChance(i)));
        }
        if (outputItems.isEmpty()) return null;
        return outputItems;
    }

    public static ArrayList<RecipeFluid> getInputFluids(GTRecipe gtRecipe) {
        ArrayList<RecipeFluid> inputFluids = new ArrayList<>();
        for (int i = 0; i < gtRecipe.mFluidInputs.length; i++) {
            final FluidStack stack = gtRecipe.mFluidInputs[i];
            inputFluids.add(
                new RecipeFluid(stack).withNBT(stack)
                    .withChance(gtRecipe.getFluidInputChance(i)));
        }
        if (inputFluids.isEmpty()) return null;
        return inputFluids;
    }

    public static ArrayList<RecipeFluid> getOutputFluids(GTRecipe gtRecipe) {
        ArrayList<RecipeFluid> outputFluids = new ArrayList<>();
        for (int i = 0; i < gtRecipe.mFluidOutputs.length; i++) {
            final FluidStack stack = gtRecipe.mFluidOutputs[i];
            outputFluids.add(
                new RecipeFluid(stack).withNBT(stack)
                    .withChance(gtRecipe.getFluidOutputChance(i)));
        }
        if (outputFluids.isEmpty()) return null;
        return outputFluids;
    }
}
