package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Exercises configured registration copying without initializing NEI's OpenGL renderer. */
public final class AddonHandlerRegression {

    private AddonHandlerRegression() {}

    public static void main(String[] args) {
        CustomRocket registered = new CustomRocket(0, "_steam", -4, 148, "gtnl.steamrocket", "gtnl.SteamRocketHandler");
        CustomRocket copy = (CustomRocket) NEIHandlerInstances.copyRocketRegistration(registered);
        check(copy != registered && copy.arecipes != registered.arecipes, "Independent handler and cache");
        check(copy.recipeId.equals(registered.recipeId) && copy.handlerId.equals(registered.handlerId), "Custom IDs");
        check(
            copy.geometry()
                .equals(registered.geometry()),
            "All constructor geometry retained");
        check(
            copy.registry() == registered.registry() && copy.registry()
                .size() == 4,
            "Complete shared registry");
        copy.arecipes.addAll(copy.registry());
        check(copy.arecipes.size() == 4 && registered.arecipes.isEmpty(), "Loading copy leaves registration untouched");
        rocketInputBindings();
    }

    private static void rocketInputBindings() {
        ItemStack part = new ItemStack(new Item());
        ItemStack[] inputs = { part, part, part, part, null, null, part };
        int[][] positions = { { 121, 3 }, { 121, 21 }, { 103, 12 }, { 139, 12 }, { 103, 30 }, { 139, 30 }, { 40, 12 } };
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        NEIDynamicSlots.addRocketInputs(groups, positions, inputs);
        List<Map<String, Object>> slots = groups.get("itemInputs");
        check(slots.size() == 5, "Empty fuel positions do not create recipe inputs");
        check(
            slots.get(4)
                .get("index")
                .equals(5),
            "Nose cone binds fifth exported input");
        check(
            slots.get(4)
                .get("x")
                .equals(40)
                && slots.get(4)
                    .get("y")
                    .equals(12),
            "Nose cone keeps seventh source position");
        check(inputs[4] == null && inputs[5] == null, "Original GT recipe remains unchanged");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static class Rocket {

        private final int tier;
        private final String textureSuffix;
        private final int y;
        private final int h;
        private List<Integer> recipes = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
        final List<Integer> arecipes = new ArrayList<>();

        Rocket(int tier, String textureSuffix, int y, int h) {
            this.tier = tier;
            this.textureSuffix = textureSuffix;
            this.y = y;
            this.h = h;
        }

        String geometry() {
            return tier + textureSuffix + ":" + y + ":" + h;
        }

        List<Integer> registry() {
            return recipes;
        }
    }

    public static final class CustomRocket extends Rocket {

        public final String recipeId;
        public final String handlerId;

        public CustomRocket(int tier, String textureSuffix, int y, int h, String recipeId, String handlerId) {
            super(tier, textureSuffix, y, h);
            this.recipeId = recipeId;
            this.handlerId = handlerId;
        }
    }
}
