package com.iouter.gtnhdumper.common.recipe;

import static tectech.util.CommonValues.EOH_TIER_FANCY_NAMES;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonObject;
import com.gtnewhorizons.modularui.api.drawable.FallbackableUITexture;
import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeFluid;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.Transformer;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.ModContainer;
import gregtech.api.enums.Materials;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import tectech.TecTech;
import tectech.recipe.EyeOfHarmonyFrontend;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.recipe.EyeOfHarmonyRecipeStorage;
import tectech.util.FluidStackLong;
import tectech.util.ItemStackLong;

public class GTDefaultHandlerRecipe extends BaseHandlerRecipe {

    public GTDefaultHandlerRecipe(GTNEIDefaultHandler handler) {
        super(handler);
    }

    @Override
    public void addJsonObject(JsonObject json, IRecipeHandler baseHandler) {
        if (!(baseHandler instanceof GTNEIDefaultHandler handler)) {
            return;
        }
        RecipeMap<?> recipeMap = handler.getRecipeMap();

        if (ModFilter.current()
            .isActive()) {
            RecipeCategory category = getRecipeCategory(handler);
            long unknown = recipeMap.getBackend()
                .getAllRecipes()
                .stream()
                .filter(recipe -> recipe.getRecipeCategory() == category && !recipe.mHidden)
                .filter(recipe -> originalOwner(recipe) == null)
                .count();
            json.addProperty("recipesWithoutSource", unknown);
            if (recipeMap.getFrontend() instanceof EyeOfHarmonyFrontend) {
                json.addProperty("sourceFilterUnsupported", true);
            }
        }

        json.addProperty("progressBar", Utils.getAfterLastChar(getProgressBar(getUIProperties(handler)), '/'));

        int amperage = recipeMap.getAmperage();
        if (amperage > 1) json.addProperty("amperage", amperage);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler baseHandler) {
        if (!(baseHandler instanceof GTNEIDefaultHandler handler)) {
            return null;
        }
        List<GTDumpedRecipe> recipes = new ArrayList<>();
        RecipeCategory category = getRecipeCategory(handler);
        RecipeMap<?> recipeMap = handler.getRecipeMap();
        RecipeMapBackend recipeMapBackend = recipeMap.getBackend();
        Collection<GTRecipe> gtRecipes = recipeMapBackend.getAllRecipes();
        ModFilter filter = ModFilter.current();
        if (recipeMap.getFrontend() instanceof EyeOfHarmonyFrontend) {
            // This custom storage does not retain GTRecipe's owner history.
            if (filter.isActive() && !RecipeItemVariants.isCollecting()) return recipes;
            try {
                EyeOfHarmonyRecipeStorage storage = TecTech.eyeOfHarmonyRecipeStorage;
                Class<?> clazz = EyeOfHarmonyRecipeStorage.class;
                Field recipeHashMapField = clazz.getDeclaredField("recipeHashMap");
                recipeHashMapField.setAccessible(true);
                @SuppressWarnings("unchecked")
                HashMap<String, EyeOfHarmonyRecipe> recipeHashMap = (HashMap<String, EyeOfHarmonyRecipe>) recipeHashMapField
                    .get(storage);
                for (EyeOfHarmonyRecipe recipe : recipeHashMap.values()) {
                    try {
                        ItemStack planetItem = recipe.getRecipeTriggerItem()
                            .copy();
                        planetItem.stackSize = 0;
                        ArrayList<Object> inputItems = new ArrayList<>();
                        inputItems.add(new RecipeItem(planetItem));
                        ArrayList<RecipeFluid> inputFluids = new ArrayList<>();
                        inputFluids.add(
                            new RecipeFluid(Materials.Hydrogen.getGas(0)).withAmount(recipe.getHydrogenRequirement()));
                        inputFluids
                            .add(new RecipeFluid(Materials.Helium.getGas(0)).withAmount(recipe.getHeliumRequirement()));
                        ArrayList<Object> outputItems = new ArrayList<>();
                        for (ItemStackLong itemStackLong : recipe.getOutputItems()) {
                            outputItems
                                .add(new RecipeItem(itemStackLong.itemStack).withAmount(itemStackLong.stackSize));
                        }
                        ArrayList<RecipeFluid> outputFluids = new ArrayList<>();
                        for (FluidStackLong fluidStackLong : recipe.getOutputFluids()) {
                            outputFluids
                                .add(new RecipeFluid(fluidStackLong.fluidStack).withAmount(fluidStackLong.amount));
                        }
                        Map<String, Object> metadata = new LinkedHashMap<>();
                        metadata.put("spacetimeCasingTierRequired", recipe.getSpacetimeCasingTierRequired());
                        metadata.put(
                            "spacetimeCasingTierName",
                            EnumChatFormatting.getTextWithoutFormattingCodes(
                                EOH_TIER_FANCY_NAMES[(int) recipe.getSpacetimeCasingTierRequired()]));
                        // Total EU per recipe, not EU/t. Keep the long values without display rounding.
                        metadata.put("euOutput", recipe.getEUOutput());
                        metadata.put("euStartCost", recipe.getEUStartCost());
                        // Raw ratios: 0.55 means 55% success; 1.5 means 150% energy efficiency.
                        metadata.put("baseRecipeSuccessChance", recipe.getBaseRecipeSuccessChance());
                        metadata.put("recipeEnergyEfficiency", recipe.getRecipeEnergyEfficiency());
                        recipes.add(
                            new GTDumpedRecipe(
                                inputItems,
                                inputFluids,
                                outputItems,
                                outputFluids,
                                null,
                                0,
                                recipe.getRecipeTimeInTicks(),
                                0,
                                metadata));

                    } catch (RuntimeException | LinkageError e) {
                        if (!RecipeItemVariants.isCollecting()) throw e;
                        RecipeItemVariants.reportFailure("GTDefaultHandlerRecipe.java entry", e);
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                RecipeItemVariants.reportFailure("Eye of Harmony storage", e);
            }
            return recipes;
        }
        gtRecipes.stream()
            .filter(gtRecipe -> gtRecipe.getRecipeCategory() == category)
            .filter(
                gtRecipe -> RecipeItemVariants.isCollecting() || !filter.isActive()
                    || filter.matchesOriginalOwner(
                        gtRecipe.owners == null ? null
                            : gtRecipe.owners.stream()
                                .map(owner -> owner == null ? null : owner.getModId())
                                .collect(Collectors.toList())))
            .map(recipe -> {
                if (!RecipeItemVariants.isCollecting()) return Transformer.transformGTRecipe(recipe);
                try {
                    return Transformer.transformGTRecipe(recipe);
                } catch (Exception | LinkageError e) {
                    RecipeItemVariants.reportFailure(
                        handler.getClass()
                            .getName() + " recipe",
                        e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .forEach(recipes::add);
        return recipes;
    }

    public static String originalOwner(GTRecipe recipe) {
        if (recipe.owners == null || recipe.owners.isEmpty()) return null;
        ModContainer owner = recipe.owners.get(0);
        return owner == null ? null : owner.getModId();
    }

    public static BasicUIProperties getUIProperties(GTNEIDefaultHandler handler) {
        try {
            Class<?> clazz = handler.getClass();
            Field field = clazz.getDeclaredField("uiProperties");
            field.setAccessible(true);
            return (BasicUIProperties) field.get(handler);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getProgressBar(BasicUIProperties ui) {
        if (ui == null) return null;
        final FallbackableUITexture texture = ui.progressBarTexture;
        if (texture == null) return null;
        return texture.get().location.toString();
    }

    public static RecipeCategory getRecipeCategory(GTNEIDefaultHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }

        try {
            // 1. 获取字段对象（使用精确字段名）
            Field recipeCategoryField = GTNEIDefaultHandler.class.getDeclaredField("recipeCategory");

            // 2. 突破访问限制（处理protected修饰符）
            recipeCategoryField.setAccessible(true);

            // 3. 从handler实例中获取字段值
            Object value = recipeCategoryField.get(handler);

            // 4. 类型安全转换
            if (value == null) {
                return null; // 允许字段值为null
            }
            if (value instanceof RecipeCategory) {
                return (RecipeCategory) value;
            }
            throw new ClassCastException("Field value is not a RecipeCategory instance");

        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Critical error: recipeCategory field missing in GTNEIDefaultHandler", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access recipeCategory field", e);
        }
    }

    public static class GTDumpedRecipe {

        private final ArrayList<Object> inputItems;
        private final ArrayList<RecipeFluid> inputFluids;
        private final ArrayList<Object> outputItems;
        private final ArrayList<RecipeFluid> outputFluids;
        private final ArrayList<Object> otherItems;
        private final Integer eut;
        private final Long duration;
        private Integer specialValue;
        private final Map<String, Object> metadata;
        private String sourceMod;
        private List<String> neiDescription;
        private String neiDescriptionError;
        private Integer burnTime;
        private List<String> allowedBoilerTypes;
        private Map<String, Integer> fluidDurations;
        private thaumcraft.api.aspects.AspectList inputAspects;

        public GTDumpedRecipe withInputAspects(thaumcraft.api.aspects.AspectList aspects) {
            inputAspects = aspects;
            return this;
        }

        public GTDumpedRecipe withDisplayData(GTRecipe recipe) {
            if (RecipeItemVariants.isCollecting()) return this;
            // 板材切割机按切削液保存实际耗时（tick），顺序与NEI时间列表一致。
            if (recipe.mSpecialItems instanceof gregtech.loaders.postload.recipes.FakeCuttingSpecialInfo) {
                try {
                    Field field = recipe.mSpecialItems.getClass()
                        .getDeclaredField("fluidsToDurations");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<net.minecraftforge.fluids.FluidStack, Integer> durations = (Map<net.minecraftforge.fluids.FluidStack, Integer>) field
                        .get(recipe.mSpecialItems);
                    fluidDurations = new LinkedHashMap<>();
                    durations.forEach(
                        (fluid, ticks) -> fluidDurations.put(
                            fluid.getFluid()
                                .getName(),
                            ticks));
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Cannot read cutting fluid durations", error);
                }
            }
            if ("gt.recipe.largeboilerfakefuels".equals(recipe.getRecipeCategory().recipeMap.unlocalizedName)
                && recipe.mInputs.length > 0) {
                // 大型锅炉固体燃料：mDuration是占位值，保留实际燃烧tick与游戏允许的锅炉类型。
                ItemStack fuel = recipe.getRepresentativeInput(0);
                burnTime = gregtech.api.util.GTModHandler.getFuelValue(fuel);
                allowedBoilerTypes = new ArrayList<>();
                allowedBoilerTypes.add("bronze");
                if (burnTime >= 1200) allowedBoilerTypes.add("steel");
                if (gregtech.api.recipe.maps.LargeBoilerFuelBackend.isAllowedFuel(fuel)) {
                    allowedBoilerTypes.add("titanium");
                    allowedBoilerTypes.add("tungstensteel");
                }
            }
            // IC2核反应堆的模型公式暂保留游戏原文；其余机器从已有数值与metadata绘制信息栏。
            if (!"gt.recipe.ic2nuke".equals(recipe.getRecipeCategory().recipeMap.unlocalizedName)) return this;
            try {
                neiDescription = gregtech.nei.DumperRecipeDisplayInfo
                    .describe(recipe, recipe.getRecipeCategory().recipeMap);
            } catch (RuntimeException | LinkageError error) {
                neiDescriptionError = error.getClass()
                    .getName() + ": "
                    + error.getMessage();
                RecipeItemVariants.reportFailure("GT recipe description", error);
            }
            return this;
        }

        public GTDumpedRecipe withSourceMod(String sourceMod) {
            this.sourceMod = sourceMod;
            return this;
        }

        public GTDumpedRecipe(ArrayList<Object> inputItems, ArrayList<RecipeFluid> inputFluids,
            ArrayList<Object> outputItems, ArrayList<RecipeFluid> outputFluids, ArrayList<Object> otherItems, int eut,
            long duration, int specialValue, Map<String, Object> metadata) {
            this.inputItems = inputItems;
            this.inputFluids = inputFluids;
            this.outputItems = outputItems;
            this.outputFluids = outputFluids;
            this.otherItems = otherItems;
            this.eut = eut;
            this.duration = duration;
            this.metadata = metadata;
            if (specialValue != 0) this.specialValue = specialValue;
        }
    }
}
