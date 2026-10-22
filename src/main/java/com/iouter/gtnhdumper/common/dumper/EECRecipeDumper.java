package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.recipe.base.RecipeFluid;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;

/** Adds KubaTech's final Extreme Entity Crusher recipe map to the normal recipe dump. */
public final class EECRecipeDumper {

    public static final String RECIPE_NAME = "Extreme Entity Crusher";

    private EECRecipeDumper() {}

    public static void dump() throws Exception {
        File outputFile = ModFilter.current()
            .outputFile("recipes/kubatech/ExtremeEntityCrusher.json");
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(outputFile)) {
            GTNHDumper.GSON.toJson(build(), writer);
        }
        GTNHDumper.info("已写入：" + outputFile.getAbsolutePath());
    }

    static JsonObject build() throws Exception {
        Map<?, ?> eecRecipes = (Map<?, ?>) field(Class.forName("kubatech.loaders.MobHandlerLoader"), "recipeMap");
        List<EECRecipe> recipes = new ArrayList<>();
        Map<String, Object> sortedRecipes = new TreeMap<>();
        double playerOnlyModifier = ((Number) field(
            Class.forName("kubatech.config.Config$MobHandler"),
            "playerOnlyDropsModifier")).doubleValue();
        for (Map.Entry<?, ?> entry : eecRecipes.entrySet()) {
            sortedRecipes.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        for (Map.Entry<String, Object> entry : sortedRecipes.entrySet()) {
            try {
                String entityName = entry.getKey();
                Object eecRecipe = entry.getValue();
                Object mobRecipe = field(eecRecipe, "recipe");

                ArrayList<Object> inputItems = new ArrayList<>();
                inputItems.add(new RecipeItem(getPoweredSpawner(entityName)));
                ArrayList<Object> outputItems = new ArrayList<>();
                ArrayList<Object> otherItems = new ArrayList<>();
                for (Object drop : (List<?>) field(eecRecipe, "mOutputs")) {
                    ItemStack stack = ((ItemStack) field(drop, "stack")).copy();
                    Object damages = field(drop, "damages");
                    if (stack.isItemStackDamageable() && damages instanceof Map && ((Map<?, ?>) damages).size() > 1) {
                        stack.setItemDamage(0);
                    }
                    int chance = ((Number) field(drop, "chance")).intValue();
                    RecipeItem item = new RecipeItem(stack);
                    Object normalChance = getChanceModifier(drop, "NormalChance");
                    if (normalChance != null) {
                        chance = (int) (((Number) field(normalChance, "chance")).doubleValue() * 100d);
                    }
                    if ((Boolean) field(drop, "playerOnly")) {
                        chance = Math.max(1, (int) (chance * playerOnlyModifier));
                    }
                    item.withChance(chance);
                    if (getChanceModifier(drop, "DropsOnlyWithEnchant") != null) {
                        otherItems.add(item);
                    } else {
                        outputItems.add(item);
                    }
                }
                recipes.add(
                    new EECRecipe(
                        inputItems,
                        outputItems,
                        otherItems,
                        ((Number) field(eecRecipe, "mEUt")).intValue(),
                        ((Number) field(eecRecipe, "mDuration")).intValue(),
                        ((Number) field(mobRecipe, "maxEntityHealth")).floatValue(),
                        entityName,
                        getInfernalStatus(
                            (Boolean) field(mobRecipe, "alwaysinfernal"),
                            (Boolean) field(mobRecipe, "infernalityAllowed"))));

            } catch (Exception | LinkageError e) {
                if (!RecipeItemVariants.isCollecting()) throw e;
                RecipeItemVariants.reportFailure("EECRecipeDumper.java entry", e);
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("name", RECIPE_NAME);
        result.add("catalysts", new JsonArray());
        result.addProperty("identifier", "kubatech.eec");
        result.addProperty("source", "kubatech.loaders.MobHandlerLoader.recipeMap");
        result.addProperty("markedItem", "null");
        result.add("recipes", GTNHDumper.GSON.toJsonTree(recipes));
        return result;
    }

    private static ItemStack getPoweredSpawner(String entityName) {
        Block poweredSpawner = Block.getBlockFromName("EnderIO:blockPoweredSpawner");
        if (poweredSpawner == null) {
            throw new IllegalStateException("Ender IO powered spawner is not registered");
        }
        ItemStack stack = new ItemStack(poweredSpawner);
        stack.stackTagCompound = new NBTTagCompound();
        stack.stackTagCompound.setBoolean("eio.abstractMachine", true);
        stack.stackTagCompound.setString("mobType", entityName);
        return stack;
    }

    private static Object getChanceModifier(Object drop, String modifierName) throws Exception {
        for (Object modifier : (List<?>) field(drop, "chanceModifiers")) {
            if (modifier.getClass()
                .getSimpleName()
                .equals(modifierName)) {
                return modifier;
            }
        }
        return null;
    }

    private static String getInfernalStatus(boolean alwaysInfernal, boolean infernalityAllowed) {
        if (alwaysInfernal) {
            return "always";
        }
        return infernalityAllowed ? "allowed" : "never";
    }

    private static Object field(Object target, String... names) throws Exception {
        Class<?> start = target instanceof Class ? (Class<?>) target : target.getClass();
        for (String name : names) {
            for (Class<?> current = start; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target instanceof Class ? null : target);
                } catch (NoSuchFieldException ignored) {}
            }
        }
        throw new NoSuchFieldException(start.getName() + " " + Arrays.toString(names));
    }

    private static class EECRecipe {

        private final ArrayList<Object> inputItems;
        private final ArrayList<RecipeFluid> inputFluids = new ArrayList<>();
        private final ArrayList<Object> outputItems;
        private final ArrayList<RecipeFluid> outputFluids = new ArrayList<>();
        private final ArrayList<Object> otherItems;
        private final int eut;
        private final int duration;
        private final float health;
        private final String entityName;
        private final String infernalstatus;

        private EECRecipe(ArrayList<Object> inputItems, ArrayList<Object> outputItems, ArrayList<Object> otherItems,
            int eut, int duration, float health, String entityName, String infernalstatus) {
            this.inputItems = inputItems;
            this.outputItems = outputItems;
            this.otherItems = otherItems;
            this.eut = eut;
            this.duration = duration;
            this.health = health;
            this.entityName = entityName;
            this.infernalstatus = infernalstatus;
        }
    }
}
