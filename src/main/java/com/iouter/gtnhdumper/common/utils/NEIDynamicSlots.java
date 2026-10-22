package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;

/** Slots added by addon overlays rather than ModularUI's inventory widgets. */
public final class NEIDynamicSlots {

    private NEIDynamicSlots() {}

    public static boolean supports(GTNEIDefaultHandler handler) {
        String name = handler.getRecipeMap()
            .getFrontend()
            .getClass()
            .getName();
        return name.equals("com.science.gtnl.common.gui.recipe.RocketAssemblerFrontend")
            || name.equals("com.science.gtnl.common.gui.recipe.ElectrocellGeneratorFrontend");
    }

    public static Map<String, List<Map<String, Object>>> read(GTNEIDefaultHandler handler, int index)
        throws ReflectiveOperationException {
        GTRecipe recipe = ((GTNEIDefaultHandler.CachedDefaultRecipe) handler.arecipes.get(index)).mRecipe;
        Class<?> frontend = handler.getRecipeMap()
            .getFrontend()
            .getClass();
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        if (frontend.getName()
            .equals("com.science.gtnl.common.gui.recipe.RocketAssemblerFrontend")) {
            int tier = recipe.mSpecialValue;
            if (tier < 1 || tier > 8) return groups;
            // Read the same public coordinate tables as drawNEIOverlays, without its GL or global init cache.
            int[][] inputs = (int[][]) frontend.getField("rocketT" + tier + "Inputs")
                .get(null);
            int[] output = (int[]) frontend.getField("rocketT" + tier + "Output")
                .get(null);
            addRocketInputs(groups, inputs, recipe.mInputs);
            if (recipe.mOutputs.length > 0 && recipe.mOutputs[0] != null)
                add(groups, "itemOutputs", output[0], output[1], 1);
        } else if (frontend.getName()
            .equals("com.science.gtnl.common.gui.recipe.ElectrocellGeneratorFrontend")) {
                // Electrocell cycles all fluid inputs in one display slot, above the normal window origin.
                if (recipe.mFluidInputs.length > 0) add(groups, "fluidInputs", 75, -3, 1);
            }
        return groups;
    }

    static void addRocketInputs(Map<String, List<Map<String, Object>>> groups, int[][] positions, ItemStack[] inputs) {
        int exportedIndex = 0;
        for (int i = 0; i < inputs.length; i++) {
            // Transformer omits empty GT inputs. Geometry uses the original array position, binding uses the exported
            // list.
            if (inputs[i] != null) add(groups, "itemInputs", positions[i][0], positions[i][1], ++exportedIndex);
        }
    }

    private static void add(Map<String, List<Map<String, Object>>> groups, String group, int x, int y, int index) {
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("x", x);
        slot.put("y", y);
        slot.put("index", index);
        groups.computeIfAbsent(group, key -> new ArrayList<>())
            .add(slot);
    }
}
