package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.iouter.gtnhdumper.common.recipe.base.RecipeFluid;

/** 匠魂冶炼炉熔化：按实际注册表导出，避免NEI分组首项失效导致空产物和20°C。 */
public final class TConstructMeltingRecipes {

    private TConstructMeltingRecipes() {}

    public static List<Map<String, Object>> read() throws ReflectiveOperationException {
        Class<?> smeltery = Class.forName("tconstruct.library.crafting.Smeltery");
        Map<?, ?> recipes = (Map<?, ?>) smeltery.getMethod("getSmeltingList")
            .invoke(null);
        Map<?, ?> temperatures = (Map<?, ?>) smeltery.getMethod("getTemperatureList")
            .invoke(null);
        Class<?> wrapper = Class.forName("mantle.utils.ItemMetaWrapper");
        Method groupName = smeltery.getMethod("getSmeltingGroup", wrapper);
        Map<List<Object>, List<ItemStack>> inputs = new LinkedHashMap<>();
        Map<List<Object>, FluidStack> outputs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : recipes.entrySet()) {
            Object key = entry.getKey();
            FluidStack fluid = (FluidStack) entry.getValue();
            if (fluid == null || fluid.amount <= 0) continue;
            Item item = (Item) wrapper.getField("item")
                .get(key);
            int damage = ((Number) wrapper.getField("meta")
                .get(key)).intValue();
            ItemStack stack = new ItemStack(item, 1, damage);
            Number temperature = (Number) temperatures.get(key);
            if (temperature == null)
                throw new IllegalStateException("Missing melting temperature: " + Utils.getItemKey(stack));
            Object group = groupName.invoke(null, key);
            // 不同流体、数量、NBT或温度分别成方；没有组名的注册项保持独立。
            List<Object> identity = java.util.Arrays.asList(
                group == null ? Utils.getItemKey(stack) : group,
                fluid.getFluid()
                    .getName(),
                fluid.amount,
                fluid.tag,
                temperature.intValue());
            inputs.computeIfAbsent(identity, ignored -> new ArrayList<>())
                .add(stack);
            outputs.putIfAbsent(identity, fluid);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map.Entry<List<Object>, List<ItemStack>>> groups = new ArrayList<>(inputs.entrySet());
        groups.sort(
            Comparator.comparing(
                entry -> entry.getKey()
                    .toString()));
        for (Map.Entry<List<Object>, List<ItemStack>> entry : groups) {
            List<ItemStack> stacks = entry.getValue();
            stacks.sort(Comparator.comparing(Utils::getItemKey));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(
                "inputItems",
                Collections.singletonList(RecipeUtil.getRecipeItems(stacks.toArray(new ItemStack[0]))));
            row.put("outputItems", Collections.emptyList());
            FluidStack fluid = outputs.get(entry.getKey());
            row.put("outputFluids", Collections.singletonList(new RecipeFluid(fluid).withNBT(fluid)));
            row.put(
                "temperature",
                entry.getKey()
                    .get(4));
            result.add(row);
        }
        return result;
    }
}
