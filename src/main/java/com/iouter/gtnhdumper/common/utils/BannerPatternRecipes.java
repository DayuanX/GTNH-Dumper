package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** 旗帜底色与图案染色分别展开；同色染料仍作为同一槽的候选。 */
public final class BannerPatternRecipes {

    private BannerPatternRecipes() {}

    public static List<Map<String, Object>> read(TemplateRecipeHandler handler) throws ReflectiveOperationException {
        Method colour = handler.getClass()
            .getDeclaredMethod("getEnumColour", ItemStack.class);
        colour.setAccessible(true);
        Function<ItemStack, Object> dyeColour = item -> {
            try {
                return colour.invoke(handler, item);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot resolve banner dye colour", e);
            }
        };
        List<Map<String, Object>> result = new ArrayList<>();
        for (TemplateRecipeHandler.CachedRecipe recipe : handler.arecipes) {
            List<PositionedStack> ingredients = recipe.getIngredients();
            List<ItemStack[]> original = new ArrayList<>();
            List<ItemStack> displayed = new ArrayList<>();
            for (PositionedStack stack : ingredients) {
                original.add(stack.items);
                displayed.add(stack.item);
            }
            try {
                Item banner = recipe.getResult().item.getItem();
                for (List<ItemStack[]> pair : colorPairs(ingredients, banner, dyeColour)) {
                    for (int i = 0; i < ingredients.size(); i++) {
                        PositionedStack stack = ingredients.get(i);
                        // getResult 会重新调用 getIngredients；缩减 items 才能固定其轮播候选。
                        stack.items = pair.get(i);
                        stack.item = stack.items[0];
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("inputItems", Utils.getRecipeItems(ingredients));
                    data.put("outputItems", Utils.getRecipeItems(recipe.getResult()));
                    result.add(data);
                }
            } finally {
                for (int i = 0; i < ingredients.size(); i++) {
                    ingredients.get(i).items = original.get(i);
                    ingredients.get(i).item = displayed.get(i);
                }
            }
        }
        return result;
    }

    /** 只展开实际底色候选和所有染料槽共有的颜色；单底色查询保持单底色。 */
    public static List<List<ItemStack[]>> colorPairs(List<PositionedStack> ingredients, Item banner,
        Function<ItemStack, ?> dyeColour) {
        int bannerSlot = -1;
        List<Map<Object, List<ItemStack>>> dyes = new ArrayList<>();
        List<Object> colors = null;
        for (int slot = 0; slot < ingredients.size(); slot++) {
            ItemStack[] choices = ingredients.get(slot).items;
            if (choices.length == 0) throw new IllegalArgumentException("Empty banner ingredient slot");
            Map<Object, List<ItemStack>> byColor = new LinkedHashMap<>();
            boolean isBanner = choices[0].getItem() == banner;
            for (ItemStack item : choices) {
                if ((item.getItem() == banner) != isBanner)
                    throw new IllegalArgumentException("Mixed banner and material candidates");
                if (!isBanner) {
                    Object color = dyeColour.apply(item);
                    if (color != null) byColor.computeIfAbsent(color, ignored -> new ArrayList<>())
                        .add(item);
                }
            }
            if (isBanner) {
                if (bannerSlot != -1) throw new IllegalArgumentException("Multiple banner inputs");
                bannerSlot = slot;
            }
            dyes.add(byColor);
            if (!byColor.isEmpty()) {
                if (colors == null) colors = new ArrayList<>(byColor.keySet());
                else colors.retainAll(byColor.keySet());
            }
        }
        if (bannerSlot == -1) throw new IllegalArgumentException("Missing banner input");
        // 带特殊材料、无染料的图案不写 Color 标签，沿用原处理器的产物构造。
        if (colors == null) colors = Collections.singletonList(null);
        if (colors.isEmpty()) throw new IllegalArgumentException("Banner dye slots share no colour");
        List<List<ItemStack[]>> pairs = new ArrayList<>();
        for (ItemStack base : ingredients.get(bannerSlot).items) {
            for (Object color : colors) {
                List<ItemStack[]> pair = new ArrayList<>();
                for (int slot = 0; slot < ingredients.size(); slot++) {
                    if (slot == bannerSlot) pair.add(new ItemStack[] { base });
                    else if (dyes.get(slot)
                        .isEmpty()) pair.add(ingredients.get(slot).items);
                    else pair.add(
                        dyes.get(slot)
                            .get(color)
                            .toArray(new ItemStack[0]));
                }
                pairs.add(pair);
            }
        }
        return pairs;
    }
}
