package com.iouter.gtnhdumper.common.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Better Questing：任务名称与标识来自当前玩家可见的NEI任务缓存，奖励属于输出。 */
public class QuestHandlerRecipe extends BaseHandlerRecipe {

    public QuestHandlerRecipe(IRecipeHandler handler) {
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
        TemplateRecipeHandler loaded = (TemplateRecipeHandler) template.getAllRecipeHandler();
        template.arecipes.clear();
        template.arecipes.addAll(loaded.arecipes);
        List<Map<String, Object>> result = new ArrayList<>();
        if (RecipeItemVariants.isCollecting()) return result;
        try {
            for (TemplateRecipeHandler.CachedRecipe recipe : template.arecipes) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("inputItems", Utils.getRecipeItems(recipe.getIngredients()));
                data.put("outputItems", Utils.getRecipeItems(recipe.getOtherStacks()));
                data.put("questName", field(recipe, "questName"));
                data.put("questId", field(recipe, "questID").toString());
                result.add(data);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot export Better Questing recipe details", e);
        }
        return result;
    }
}
