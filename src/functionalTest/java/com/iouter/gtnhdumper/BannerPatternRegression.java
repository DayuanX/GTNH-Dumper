package com.iouter.gtnhdumper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.iouter.gtnhdumper.common.utils.BannerPatternRecipes;

import codechicken.nei.PositionedStack;

/** 底色与图案颜色独立配对，同色染料可以互换，多染料槽不能跨色混配。 */
public final class BannerPatternRegression {

    private BannerPatternRegression() {}

    public static void main(String[] args) {
        Item banner = new Item();
        Item dye = new Item();
        Item alternateDye = new Item();
        Item stencil = new Item();
        Function<ItemStack, Object> color = stack -> stack.getItem() == dye || stack.getItem() == alternateDye
            ? stack.getItemDamage()
            : null;
        ItemStack[] bases = new ItemStack[16];
        ItemStack[] dyes = new ItemStack[17];
        for (int i = 0; i < 16; i++) {
            bases[i] = new ItemStack(banner, 1, i);
            dyes[i] = new ItemStack(dye, 1, i);
        }
        dyes[16] = new ItemStack(alternateDye, 1, 3);
        List<PositionedStack> inputs = Arrays.asList(slot(bases), slot(dyes), slot(dyes));
        ItemStack[] originalBases = inputs.get(0).items;
        ItemStack[] originalDyes = inputs.get(1).items;
        List<List<ItemStack[]>> pairs = BannerPatternRecipes.colorPairs(inputs, banner, color);
        check(pairs.size() == 256, "16 base colours times 16 pattern colours");
        boolean[][] seen = new boolean[16][16];
        for (List<ItemStack[]> pair : pairs) {
            check(pair.get(0).length == 1, "A paired output has exactly one banner base");
            int base = pair.get(0)[0].getItemDamage();
            int pattern = pair.get(1)[0].getItemDamage();
            check(!seen[base][pattern], "Colour combination must not be duplicated");
            seen[base][pattern] = true;
            for (int slot : new int[] { 1, 2 }) {
                check(pair.get(slot).length == (pattern == 3 ? 2 : 1), "Keep equivalent dye materials");
                for (ItemStack candidate : pair.get(slot))
                    check(candidate.getItemDamage() == pattern, "All dye slots must agree with the output colour");
            }
        }
        check(
            inputs.get(0).items == originalBases && inputs.get(1).items == originalDyes,
            "Pair planning must not mutate NEI caches");
        List<PositionedStack> fixedBase = Arrays.asList(slot(new ItemStack[] { bases[7] }), slot(dyes));
        check(
            BannerPatternRecipes.colorPairs(fixedBase, banner, color)
                .size() == 16,
            "Do not expand a fixed base colour");
        List<PositionedStack> undyed = Arrays.asList(slot(bases), slot(new ItemStack[] { new ItemStack(stencil) }));
        check(
            BannerPatternRecipes.colorPairs(undyed, banner, color)
                .size() == 16,
            "Special patterns without dye");
        List<PositionedStack> restricted = new ArrayList<>(fixedBase);
        restricted.add(slot(new ItemStack[] { dyes[3] }));
        List<List<ItemStack[]>> restrictedPairs = BannerPatternRecipes.colorPairs(restricted, banner, color);
        check(
            restrictedPairs.size() == 1 && restrictedPairs.get(0)
                .get(1).length == 2,
            "Use the intersection of colours accepted by all dye slots");
        System.out.println("Banner colour pairing regression passed");
    }

    private static PositionedStack slot(ItemStack[] choices) {
        return new PositionedStack(choices, 25, 6, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
