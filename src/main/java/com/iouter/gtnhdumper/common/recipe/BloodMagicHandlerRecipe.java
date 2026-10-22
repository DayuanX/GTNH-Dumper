package com.iouter.gtnhdumper.common.recipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** 血之祭坛和陨星仪式：保留 LP、等级、速率，以及陨星各产物提示与九列网格位置。 */
public class BloodMagicHandlerRecipe extends BaseHandlerRecipe {

    public BloodMagicHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /** 陨星组分按游戏的权重排序；概率保持float口径，数量使用原球体估算公式。 */
    private static List<Map<String, Object>> meteorParts(Object meteor) throws ReflectiveOperationException {
        List<Map<String, Object>> result = new ArrayList<>();
        float filler = ((Number) field(meteor, "fillerChance")).floatValue() / 100.0f;
        int radius = ((Number) field(meteor, "radius")).intValue();
        for (String role : new String[] { "ore", "filler" }) {
            float ratio = role.equals("ore") ? 1.0f - filler : filler;
            if (ratio <= 0) continue;
            List<Map<String, Object>> parts = new ArrayList<>();
            int total = 0;
            for (Object component : (List<?>) field(meteor, role.equals("ore") ? "ores" : "filler")) {
                int weight = ((Number) component.getClass()
                    .getMethod("getWeight")
                    .invoke(component)).intValue();
                total += weight;
                List<String> reagents = new ArrayList<>();
                for (Object reagent : (java.util.Collection<?>) component.getClass()
                    .getMethod("getRequiredReagents")
                    .invoke(component)) {
                    reagents.add(
                        (String) reagent.getClass()
                            .getMethod("name")
                            .invoke(reagent));
                }
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("weight", weight);
                part.put("meteorRole", role);
                part.put("reagentRequirements", reagents);
                parts.add(part);
            }
            parts.sort(Comparator.comparingInt(part -> -(Integer) part.get("weight")));
            for (Map<String, Object> part : parts) {
                float chance = ((Integer) part.remove("weight")).floatValue() / total * ratio;
                part.put("chance", chance * 10000.0);
                part.put("expectedAmount", (int) Math.ceil(4.1887903296220665 * Math.pow(radius + 0.5, 3) * chance));
            }
            result.addAll(parts);
        }
        return result;
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        TemplateRecipeHandler loaded = (TemplateRecipeHandler) template.getAllRecipeHandler();
        template.arecipes.clear();
        template.arecipes.addAll(loaded.arecipes);
        List<Map<String, Object>> result = new ArrayList<>();
        if (RecipeItemVariants.isCollecting()) return result;
        boolean meteor = "alchemicalwizardry.meteor".equals(handler.getOverlayIdentifier());
        try {
            List<?> meteors = java.util.Collections.emptyList();
            if (meteor) {
                Method sorted = template.getClass()
                    .getDeclaredMethod("getSortedMeteors");
                sorted.setAccessible(true);
                meteors = (List<?>) sorted.invoke(template);
            }
            int recipeIndex = 0;
            for (TemplateRecipeHandler.CachedRecipe recipe : template.arecipes) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("inputItems", Utils.getRecipeItems(recipe.getIngredients()));
                if (meteor) {
                    data.put(
                        "lifeEssence",
                        recipe.getClass()
                            .getMethod("getCost")
                            .invoke(recipe));
                    data.put(
                        "meteorRadius",
                        recipe.getClass()
                            .getMethod("getRadius")
                            .invoke(recipe));
                    List<Object> outputs = new ArrayList<>();
                    List<Integer> indices = new ArrayList<>();
                    List<Map<String, Object>> parts = meteorParts(meteors.get(recipeIndex++));
                    int outputIndex = 0;
                    for (PositionedStack stack : recipe.getOtherStacks()) {
                        Object converted = RecipeUtil.getRecipeItems(stack);
                        RecipeItem[] items = converted instanceof RecipeItem item ? new RecipeItem[] { item }
                            : (RecipeItem[]) converted;
                        Map<String, Object> part = parts.get(outputIndex++);
                        for (RecipeItem item : items) {
                            item.chance = part.get("chance");
                            item.expectedAmount = (Integer) part.get("expectedAmount");
                            item.meteorRole = (String) part.get("meteorRole");
                            @SuppressWarnings("unchecked")
                            List<String> reagents = (List<String>) part.get("reagentRequirements");
                            if (!reagents.isEmpty()) item.reagentRequirements = reagents;
                        }
                        outputs.add(converted);
                        // 游戏将填充方块另起一行；列表序号不能代替槽位序号。
                        indices.add((stack.rely - 37) / 18 * 9 + (stack.relx - 3) / 18 + 1);
                    }
                    data.put("outputItems", outputs);
                    data.put("meteorOutputSlots", indices);
                } else {
                    data.put("outputItems", Utils.getRecipeItems(recipe.getResult()));
                    data.put("altarTier", field(recipe, "tier"));
                    data.put("lifeEssence", field(recipe, "lp_amount"));
                    data.put("consumptionRate", field(recipe, "consumption"));
                    data.put("drainRate", field(recipe, "drain"));
                }
                result.add(data);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot export Blood Magic recipe details", e);
        }
        return result;
    }
}
