package com.iouter.gtnhdumper.common.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** 钓鱼战利品：导出分类概率及每次钓获的物品概率；不把随机展示帧当成固定属性。 */
public class MobHandlerFishingRecipe extends BaseHandlerRecipe {

    public MobHandlerFishingRecipe(IRecipeHandler handler) {
        super(handler);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        template.loadCraftingRecipes(handler.getOverlayIdentifier(), (Object) null);
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (Object recipe : template.arecipes) {
                Map<String, Object> data = new LinkedHashMap<>();
                for (Object category : (List<?>) field(recipe, "categories")) {
                    String name = ((Enum<?>) field(category, "title")).name()
                        .toLowerCase(java.util.Locale.ROOT);
                    data.put(name + "Chance", field(category, "chance"));
                    List<Object> items = new ArrayList<>();
                    for (Object value : (List<?>) field(category, "stacks")) {
                        PositionedStack stack = (PositionedStack) value;
                        Object converted = RecipeUtil.getRecipeItems(stack);
                        double chance = ((Number) field(stack, "chance")).doubleValue();
                        RecipeItem[] candidates = converted instanceof RecipeItem item ? new RecipeItem[] { item }
                            : (RecipeItem[]) converted;
                        for (RecipeItem item : candidates) {
                            item.chance = chance * 10000d;
                            if (Boolean.TRUE.equals(field(stack, "randomable"))) {
                                item.withTooltip("此条目会随机生成附魔或耐久；展示物品不代表固定随机结果。");
                            }
                        }
                        items.add(converted);
                    }
                    data.put(name + "Items", items);
                }
                result.add(data);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot export MobsInfo fishing categories", e);
        }
        return result;
    }
}
