package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** 植物酿造、魔力池与符文祭坛：导出配方真实魔力消耗，单位为魔力点。 */
public class BotaniaHandlerRecipe extends BaseHandlerRecipe {

    public BotaniaHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        TemplateRecipeHandler loaded = (TemplateRecipeHandler) template.getAllRecipeHandler();
        template.arecipes.clear();
        template.arecipes.addAll(loaded.arecipes);
        List<Map<String, Object>> result = new ArrayList<>();
        if (RecipeItemVariants.isCollecting()) return result;
        String field = handler.getClass()
            .getSimpleName()
            .equals("RecipeHandlerRunicAltar") ? "manaUsage" : "mana";
        try {
            for (TemplateRecipeHandler.CachedRecipe recipe : template.arecipes) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("inputItems", Utils.getRecipeItems(recipe.getIngredients()));
                data.put("outputItems", Utils.getRecipeItems(recipe.getResult()));
                data.put("otherItems", Utils.getRecipeItems(recipe.getOtherStacks()));
                if ("botania.lexica".equals(handler.getOverlayIdentifier())) {
                    exportLexiconEntry(data, recipe);
                } else {
                    data.put(
                        "mana",
                        recipe.getClass()
                            .getField(field)
                            .get(recipe));
                }
                result.add(data);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot export Botania mana cost", e);
        }
        return result;
    }

    /** 辞典说明与NEI使用同一翻译键；没有快速说明时沿用游戏的无说明提示。 */
    private static void exportLexiconEntry(Map<String, Object> data, TemplateRecipeHandler.CachedRecipe recipe)
        throws ReflectiveOperationException {
        Object entry = recipe.getClass()
            .getField("entry")
            .get(recipe);
        data.put("lexiconTitle", translate(entry, "getUnlocalizedName"));
        data.put("lexiconTagline", translate(entry, "getTagline"));
        Object knowledge = entry.getClass()
            .getMethod("getKnowledgeType")
            .invoke(entry);
        data.put("lexiconKnowledge", translate(knowledge, "getUnlocalizedName"));
        String itemKey = (String) Class.forName("vazkii.botania.api.lexicon.LexiconRecipeMappings")
            .getMethod("stackToString", ItemStack.class)
            .invoke(null, recipe.getResult().item);
        String key = "botania.nei.quickInfo:" + itemKey;
        String translated = StatCollector.translateToLocal(key);
        data.put(
            "lexiconQuickInfo",
            translated.equals(key) ? StatCollector.translateToLocal("botania.nei.lexicaNoInfo") : translated);
    }

    private static String translate(Object target, String method) throws ReflectiveOperationException {
        return StatCollector.translateToLocal(
            (String) target.getClass()
                .getMethod(method)
                .invoke(target));
    }
}
