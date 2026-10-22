package com.iouter.gtnhdumper.common.recipe.base;

import static com.iouter.gtnhdumper.GTNHDumper.GSON;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.google.common.base.Objects;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;

import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.TemplateRecipeHandler;

public abstract class BaseHandlerRecipe {

    private final JsonObject json = new JsonObject();

    public BaseHandlerRecipe(IRecipeHandler handler) {
        if (RecipeItemVariants.isCollecting()) {
            // Decoration failures must not prevent visiting the actual recipe storage.
            try {
                RecipeCatalysts.getRecipeCatalysts(handler)
                    .forEach(RecipeItemVariants::capture);
            } catch (Exception | LinkageError e) {
                RecipeItemVariants.reportFailure(
                    handler.getClass()
                        .getName() + " catalysts",
                    e);
            }
            try {
                String overlay = handler instanceof TemplateRecipeHandler ? handler.getOverlayIdentifier() : null;
                HandlerInfo info = GuiRecipeTab
                    .getHandlerInfo(handler.getHandlerId(), Objects.firstNonNull(overlay, "null"));
                if (info != null) RecipeItemVariants.capture(info.getItemStack());
            } catch (Exception | LinkageError e) {
                RecipeItemVariants.reportFailure(
                    handler.getClass()
                        .getName() + " marked item",
                    e);
            }
            // Some adapters return raw ItemStacks; retain their normal serializer capture path.
            serializeRecipes(handler);
            return;
        }
        json.addProperty("name", handler.getRecipeName());
        JsonArray catalysts = new JsonArray();

        RecipeCatalysts.getRecipeCatalysts(handler)
            .forEach(p -> catalysts.add(GSON.toJsonTree(RecipeUtil.getRecipeItems(p))));

        json.add("catalysts", catalysts);

        final String handlerName = handler.getHandlerId();
        final String handlerId = Objects
            .firstNonNull(handler instanceof TemplateRecipeHandler ? handler.getOverlayIdentifier() : null, "null");

        json.addProperty("identifier", handlerId);
        json.addProperty("source", handlerName);
        HandlerInfo info = GuiRecipeTab.getHandlerInfo(handlerName, handlerId);
        final ItemStack markedItemStack = info != null ? info.getItemStack() : null;
        json.add("markedItem", markedItemStack != null ? GSON.toJsonTree(markedItemStack) : GSON.toJsonTree("null"));

        addJsonObject(json, handler);

        json.add("recipes", serializeRecipes(handler));
    }

    private JsonElement serializeRecipes(IRecipeHandler handler) {
        List<?> recipes = getRecipes(handler);
        if (recipes == null) return JsonNull.INSTANCE;
        JsonArray result = new JsonArray();
        for (int index = 0; index < recipes.size(); index++) {
            Object recipe = recipes.get(index);
            try {
                result.add(GSON.toJsonTree(recipe));
            } catch (StackOverflowError error) {
                // Gson's recursive frames erase the caller from the crash stack; retain the exact source record.
                throw new IllegalStateException(
                    "Recursive recipe serialization: handler=" + handler.getHandlerId()
                        + "; overlay="
                        + (handler instanceof TemplateRecipeHandler ? handler.getOverlayIdentifier() : null)
                        + "; source="
                        + handler.getClass()
                            .getName()
                        + "; recipeIndex="
                        + index
                        + "; recordType="
                        + (recipe == null ? "null"
                            : recipe.getClass()
                                .getName()),
                    error);
            }
        }
        return result;
    }

    public void addJsonObject(JsonObject json, IRecipeHandler handler) {

    }

    public abstract List<?> getRecipes(IRecipeHandler handler);

    public JsonObject build() {
        return json;
    }
}
