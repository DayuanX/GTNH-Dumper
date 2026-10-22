package com.iouter.gtnhdumper.common.recipe;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeFluid;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** NEI专用显示数据：Ender IO能量/附魔、作物条件、装瓶机/精炼厂储罐及战利品分组。 */
public class NEIDisplayHandlerRecipe extends BaseHandlerRecipe {

    public NEIDisplayHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    public static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(
            target.getClass()
                .getName() + "."
                + name);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass()
            .getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Map<String, Object> basic(TemplateRecipeHandler.CachedRecipe recipe) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inputItems", Utils.getRecipeItems(recipe.getIngredients()));
        data.put("outputItems", Utils.getRecipeItems(recipe.getResult()));
        data.put("otherItems", Utils.getRecipeItems(recipe.getOtherStacks()));
        return data;
    }

    private static RecipeFluid tankFluid(Object positioned) throws ReflectiveOperationException {
        FluidTank tank = (FluidTank) field(positioned, "tank");
        FluidStack fluid = tank.getFluid();
        return fluid == null ? null : new RecipeFluid(fluid).withNBT(fluid);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        TemplateRecipeHandler loaded = (TemplateRecipeHandler) template.getAllRecipeHandler();
        template.arecipes.clear();
        template.arecipes.addAll(loaded.arecipes);
        List<Map<String, Object>> result = new ArrayList<>();
        if (RecipeItemVariants.isCollecting()) return result;
        String name = handler.getClass()
            .getSimpleName();
        Map<Integer, Map<String, Object>> tanks = com.iouter.gtnhdumper.common.utils.RecipeTankData.read(template);
        int recipeIndex = 0;
        try {
            for (TemplateRecipeHandler.CachedRecipe recipe : template.arecipes) {
                if (name.equals("EnchanterRecipeHandler")) {
                    enchantments(recipe, result);
                    continue;
                }
                if (name.equals("VatRecipeHandler")) {
                    vat(recipe, result);
                    continue;
                }
                Map<String, Object> data = basic(recipe);
                if (tanks.containsKey(recipeIndex)) data.putAll(tanks.get(recipeIndex));
                recipeIndex++;
                switch (name) {
                    case "NEIInfusedSeedRecipeHandler" -> {
                        // 蕴魔种子掉落：wrapper 中的掉落表是产物，chance 已按权重换算为百分数。
                        List<RecipeItem> drops = new ArrayList<>();
                        for (Object drop : (List<?>) field(field(recipe, "wrapper"), "drops")) {
                            ItemStack item = (ItemStack) field(drop, "stack");
                            float percent = ((Number) field(drop, "chance")).floatValue();
                            drops.add(new RecipeItem(item).withChance(Math.round(percent * 100)));
                        }
                        data.put("outputItems", drops);
                        data.remove("otherItems");
                    }
                    case "AlloySmelterRecipeHandler", "SagMillRecipeHandler", "SliceAndSpliceRecipeHandler", "SoulBinderRecipeHandler" -> {
                        data.put("energyRF", call(recipe, "getEnergy"));
                        if (name.equals("SoulBinderRecipeHandler"))
                            data.put("experienceLevels", call(recipe, "getExperience"));
                        if (name.equals("SagMillRecipeHandler")) {
                            List<Object> outputs = new ArrayList<>();
                            List<PositionedStack> stacks = new ArrayList<>();
                            if (recipe.getResult() != null) stacks.add(recipe.getResult());
                            stacks.addAll(recipe.getOtherStacks());
                            // getChanceForOutput compares ItemStack object identity; items[] may contain copies.
                            // The cached chance array follows result + otherOutputs in this same order.
                            float[] chances = (float[]) field(recipe, "outputChance");
                            for (int outputIndex = 0; outputIndex < stacks.size(); outputIndex++) {
                                PositionedStack stack = stacks.get(outputIndex);
                                List<RecipeItem> candidates = new ArrayList<>();
                                for (ItemStack item : stack.items) {
                                    float chance = chances[outputIndex];
                                    candidates.add(new RecipeItem(item).withChance(Math.round(chance * 10000)));
                                }
                                outputs.add(candidates.size() == 1 ? candidates.get(0) : candidates);
                            }
                            data.put("outputItems", outputs);
                            data.remove("otherItems");
                        }
                    }
                    case "NEICropsNHCropstickBreedingHandler" -> data
                        .put("additionalInformation", call(recipe, "getReqLines"));
                    case "NEICropsNHMutationPoolHandler" -> {
                        // 完整处理器按注册池顺序创建缓存；保留翻译键作为稳定身份，不解析本地化说明。
                        Object pool = new ArrayList<>(
                            com.gtnewhorizon.cropsnh.farming.registries.MutationRegistry.instance.getMutationPools())
                                .get(recipeIndex - 1);
                        String poolId = (String) call(pool, "getUnlocalisedName");
                        data.put("mutationPoolId", poolId);
                        data.put("mutationPoolName", net.minecraft.util.StatCollector.translateToLocal(poolId));
                    }
                    case "FuelRecipeHandler" -> data.put("burnTime", field(field(recipe, "fuel"), "burnTime"));
                    case "NEIHandlerMoistener" -> {
                        // 原料I1、燃料I2；燃料产物是第二输出，不是第三输入。
                        List<PositionedStack> others = recipe.getOtherStacks();
                        List<Object> inputs = new ArrayList<>();
                        inputs.add(RecipeUtil.getRecipeItems(recipe.getIngredient()));
                        List<Object> outputs = new ArrayList<>();
                        outputs.add(RecipeUtil.getRecipeItems(recipe.getResult()));
                        for (PositionedStack stack : others) {
                            if (stack.relx == 34) inputs.add(RecipeUtil.getRecipeItems(stack));
                            else if (stack.relx == 100) outputs.add(RecipeUtil.getRecipeItems(stack));
                            else throw new IllegalStateException("Unexpected moistener fuel position");
                        }
                        data.put("inputItems", inputs);
                        data.put("outputItems", outputs);
                        data.remove("otherItems");
                    }
                    case "NEIHandlerSqueezer" -> {
                        if (Boolean.TRUE.equals(field(recipe, "containerRecipe"))) {
                            for (ItemStack item : recipe.getIngredients()
                                .get(0).items) {
                                FluidStack fluid = forestry.core.fluids.FluidHelper.getFluidStackInContainer(item);
                                if (fluid == null) throw new IllegalStateException("Squeezer container has no fluid");
                                Map<String, Object> row = new LinkedHashMap<>(data);
                                row.put("inputItems", java.util.Arrays.asList(new RecipeItem(item)));
                                row.put("outputFluids", java.util.Arrays.asList(new RecipeFluid(fluid)));
                                result.add(row);
                            }
                            continue;
                        }
                    }
                    case "NEIHandlerFabricator" -> {
                        // 热电子加工台：九宫格I1–I9、模板I10、熔化原料I11。
                        List<Object> inputs = new ArrayList<>(java.util.Collections.nCopies(11, null));
                        for (PositionedStack stack : recipe.getIngredients()) {
                            int index = stack.relx == 134 && stack.rely == 6 ? 9
                                : (stack.rely - 6) / 18 * 3 + (stack.relx - 62) / 18;
                            if (index < 0 || index >= 10)
                                throw new IllegalStateException("Unexpected fabricator position");
                            inputs.set(index, RecipeUtil.getRecipeItems(stack));
                        }
                        List<PositionedStack> smelting = recipe.getOtherStacks();
                        if (!smelting.isEmpty()) inputs.set(10, RecipeUtil.getRecipeItems(smelting.get(0)));
                        data.put("inputItems", inputs);
                        data.remove("otherItems");
                        Object tank = call(recipe, "getFluidTank");
                        RecipeFluid fluid = tank == null ? null : tankFluid(tank);
                        data.put(
                            "inputFluids",
                            fluid == null ? java.util.Arrays.asList() : java.util.Arrays.asList(fluid));
                    }
                    case "NEIHandlerBottler" -> {
                        Object tank = call(recipe, "getFluidTank");
                        RecipeFluid fluid = tank == null ? null : tankFluid(tank);
                        data.put(
                            "inputFluids",
                            fluid == null ? java.util.Arrays.asList() : java.util.Arrays.asList(fluid));
                    }
                    case "RecipeHandlerRefinery" -> {
                        List<RecipeFluid> inputs = new ArrayList<>(), outputs = new ArrayList<>();
                        for (Object tank : (List<?>) call(recipe, "getFluidTanks")) {
                            Rectangle position = (Rectangle) field(tank, "position");
                            RecipeFluid fluid = tankFluid(tank);
                            if (fluid != null) (position.x == 77 ? outputs : inputs).add(fluid);
                        }
                        data.put("inputFluids", inputs);
                        data.put("outputFluids", outputs);
                        long ticks = ((Number) field(recipe, "time")).longValue();
                        long power = ((Number) field(recipe, "energy")).longValue();
                        data.put("energyRF", power * ticks);
                        data.put("powerRF", power);
                        data.put("duration", ticks);
                    }
                    case "LootBagRecipeHandler" -> {
                        List<Object> outputs = new ArrayList<>();
                        for (PositionedStack stack : recipe.getOtherStacks()) {
                            List<RecipeItem> candidates = new ArrayList<>();
                            Method tooltipMethod = stack.getClass()
                                .getMethod("getTooltip", ItemStack.class);
                            for (ItemStack item : stack.items) {
                                @SuppressWarnings("unchecked")
                                List<String> lines = (List<String>) tooltipMethod.invoke(stack, item);
                                RecipeItem converted = new RecipeItem(item);
                                if (lines != null) converted.withTooltip(lines);
                                candidates.add(converted);
                            }
                            outputs.add(candidates.size() == 1 ? candidates.get(0) : candidates);
                        }
                        data.put("outputItems", outputs);
                        data.remove("otherItems");
                    }
                    default -> throw new IllegalArgumentException("Unsupported detailed handler: " + name);
                }
                result.add(data);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot export NEI display data for " + name, e);
        }
        return result;
    }

    /** 附魔器逐等级生成独立配方，材料数量、书籍NBT和经验等级不再独立轮播。 */
    private static void enchantments(TemplateRecipeHandler.CachedRecipe cached, List<Map<String, Object>> result)
        throws ReflectiveOperationException {
        Object recipe = field(cached, "recipe");
        Enchantment enchantment = (Enchantment) call(recipe, "getEnchantment");
        int perLevel = ((Number) call(recipe, "getItemsPerLevel")).intValue();
        Class<?> tile = Class.forName("crazypants.enderio.machine.enchanter.TileEnchanter");
        Method cost = tile.getMethod("getEnchantmentCost", recipe.getClass(), int.class);
        List<PositionedStack> inputs = cached.getIngredients();
        ItemStack[] outputs = cached.getResult().items;
        for (int level = 1; level <= enchantment.getMaxLevel(); level++) {
            Map<String, Object> data = new LinkedHashMap<>();
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                ItemStack[] choices = new ItemStack[inputs.get(i).items.length];
                for (int j = 0; j < choices.length; j++) {
                    choices[j] = inputs.get(i).items[j].copy();
                    if (i == 1) choices[j].stackSize = perLevel * level;
                }
                items.add(RecipeUtil.getRecipeItems(choices));
            }
            data.put("inputItems", items);
            data.put("outputItems", java.util.Arrays.asList(new RecipeItem(outputs[level - 1])));
            data.put("enchantmentName", enchantment.getTranslatedName(level));
            data.put("enchantmentLevel", level);
            data.put("experienceLevels", cost.invoke(null, recipe, level));
            result.add(data);
        }
    }

    /** 发酵槽按实际候选组合展开；倍率决定液体数量，不能与物品独立轮播。 */
    private static void vat(TemplateRecipeHandler.CachedRecipe recipe, List<Map<String, Object>> result)
        throws ReflectiveOperationException {
        List<PositionedStack> inputs = recipe.getIngredients();
        ItemStack[] first = inputs.isEmpty() ? new ItemStack[] { null } : inputs.get(0).items;
        ItemStack[] second = inputs.size() < 2 ? new ItemStack[] { null } : inputs.get(1).items;
        ItemStack savedFirst = inputs.isEmpty() ? null : inputs.get(0).item;
        ItemStack savedSecond = inputs.size() < 2 ? null : inputs.get(1).item;
        try {
            for (ItemStack a : first) for (ItemStack b : second) {
                if (!inputs.isEmpty()) inputs.get(0).item = a;
                if (inputs.size() > 1) inputs.get(1).item = b;
                List<RecipeItem> items = new ArrayList<>();
                if (a != null) items.add(new RecipeItem(a));
                if (b != null) items.add(new RecipeItem(b));
                FluidStack in = ((FluidStack) field(recipe, "inFluid")).copy();
                FluidStack out = ((FluidStack) field(recipe, "result")).copy();
                in.amount = ((Number) call(recipe, "getInputFluidAmount")).intValue();
                out.amount = ((Number) call(recipe, "getResultFluidAmount")).intValue();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("inputItems", items);
                data.put("inputFluids", java.util.Arrays.asList(new RecipeFluid(in)));
                data.put("outputFluids", java.util.Arrays.asList(new RecipeFluid(out)));
                data.put("energyRF", call(recipe, "getEnergy"));
                data.put(
                    "itemMultipliers",
                    java.util.Arrays
                        .asList(call(recipe, "getFirstItemMultiplier"), call(recipe, "getSecondItemMultiplier")));
                result.add(data);
            }
        } finally {
            if (!inputs.isEmpty()) inputs.get(0).item = savedFirst;
            if (inputs.size() > 1) inputs.get(1).item = savedSecond;
        }
    }
}
