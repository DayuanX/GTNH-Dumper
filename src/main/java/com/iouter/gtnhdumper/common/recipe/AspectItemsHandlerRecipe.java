package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.aspectrecipeindex.ModItems;
import com.gtnewhorizons.aspectrecipeindex.common.items.ItemAspect;
import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.IRecipeHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** 包含要素的物品：直接查询NEI物品目录的要素，避免依赖新存档尚未扫描的玩家知识。 */
public class AspectItemsHandlerRecipe extends BaseHandlerRecipe {

    public AspectItemsHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        Map<Aspect, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack item : ItemList.items) {
            AspectList tags = ThaumcraftCraftingManager.getObjectTags(item);
            tags = ThaumcraftCraftingManager.getBonusTags(item, tags);
            if (tags == null) continue;
            for (Aspect aspect : tags.getAspects()) {
                if (aspect == null || tags.getAmount(aspect) <= 0) continue;
                ItemStack copy = item.copy();
                copy.stackSize = tags.getAmount(aspect);
                groups.computeIfAbsent(aspect, key -> new ArrayList<>())
                    .add(copy);
            }
        }
        List<Map<String, Object>> recipes = new ArrayList<>();
        for (Map.Entry<Aspect, List<ItemStack>> group : groups.entrySet()) {
            group.getValue()
                .sort(
                    Comparator.comparingInt((ItemStack stack) -> stack.stackSize)
                        .reversed());
            ItemStack aspect = new ItemStack(ModItems.itemAspect);
            ItemAspect.setAspect(aspect, group.getKey());
            for (int start = 0; start < group.getValue()
                .size(); start += 36) {
                List<RecipeItem> inputs = new ArrayList<>();
                for (int i = start; i < Math.min(
                    start + 36,
                    group.getValue()
                        .size()); i++)
                    inputs.add(
                        new RecipeItem(
                            group.getValue()
                                .get(i)));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("inputItems", inputs);
                data.put("outputItems", java.util.Collections.singletonList(new RecipeItem(aspect)));
                recipes.add(data);
            }
        }
        return recipes;
    }
}
