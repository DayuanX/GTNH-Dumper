package com.iouter.gtnhdumper;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.iouter.gtnhdumper.common.utils.ModFilter;

/** Runs without launching Minecraft; checks source ownership and filter input semantics. */
public final class ModFilterRegression {

    private ModFilterRegression() {}

    public static void main(String[] args) {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("minecraft", "Minecraft");
        mods.put("gregtech", "GregTech");
        mods.put("exampleaddon", "Example Addon");

        ModFilter all = new ModFilter("  ", mods);
        check(!all.isActive(), "Blank input must disable filtering");
        check(all.matchesKey("other:item"), "Full export must retain foreign items");
        check(all.matchesOriginalOwner(null), "Full export must retain recipes without owner records");
        check(
            all.relativeDirectory()
                .isEmpty(),
            "Full export must retain the existing output path");

        ModFilter gt = new ModFilter(" GrEgTeCh ", mods);
        check(gt.isActive(), "Mod IDs must ignore case and surrounding whitespace");
        check(gt.matchesKey("gregtech:machine:1234"), "Metadata variants must match the registering mod");
        check(gt.matchesKey("gregtech:machine:1234_nbtHash"), "NBT variants must match the registering mod");
        check(!gt.matchesKey("gregtechaddon:item"), "Mod IDs must match exactly, not by prefix");
        check(!gt.matchesKey("minecraft:gregtech"), "An item name is not its owner");
        check(!gt.matchesKey(null) && !gt.matchesKey("null"), "Unregistered items must not match");
        check(
            gt.relativeDirectory()
                .equals("filtered/gregtech/"),
            "Filtered output must be isolated");

        ModFilter addon = new ModFilter(" EXAMPLE ADDON ", mods);
        check(
            addon.modId()
                .equals("exampleaddon"),
            "Autorun reports the resolved ID for display-name requests");
        check(addon.matchesKey("exampleaddon:item"), "Full display names must resolve to mod IDs");
        check(!addon.matchesKey("gregtech:machine:1234"), "Changing mods must change the selected items");
        check(
            !addon.matchesItem(new ItemStack(new Item())),
            "Unregistered creative-tab items must be skipped without aborting the dump");
        check(
            !addon.matchesOriginalOwner(Arrays.asList("gregtech", "exampleaddon")),
            "A later modifier must not be treated as the original recipe source");
        check(
            addon.matchesOriginalOwner(Arrays.asList("exampleaddon", "gregtech")),
            "Recipes must retain their original source after another mod changes them");
        check(!addon.matchesOriginalOwner(null), "Missing source records must be excluded");
        check(!addon.matchesOriginalOwner(Collections.emptyList()), "Empty owner histories must be excluded");
        check(
            !addon.matchesOriginalOwner(Arrays.asList(null, "exampleaddon")),
            "An unknown original source must not fall back to a later owner");
        check(new ModFilter("", mods).matchesKey("gregtech:machine"), "Clearing must restore all items");
        expectInvalid("example", mods);
        expectInvalid("../gregtech", mods);

        mods.put("duplicate1", "Duplicate");
        mods.put("duplicate2", "Duplicate");
        expectInvalid("Duplicate", mods);
        check(
            new ModFilter("duplicate1", mods).matchesMod("duplicate1"),
            "A mod ID must disambiguate duplicate display names");
        mods.put("nameCollision", "gregtech");
        check(
            new ModFilter("gregtech", mods).matchesKey("gregtech:machine"),
            "An exact ID must take priority over another mod's display name");
        check(
            !new ModFilter("nameCollision", mods).matchesKey("gregtech:machine"),
            "A display name must never masquerade as another mod's registered namespace");
        check(
            !new ModFilter("nameCollision", mods).matchesOriginalOwner(Collections.singletonList("gregtech")),
            "Recipe source IDs must never match an unrelated display name");
        mods.put("example.mod", "Dotted ID");
        mods.put("example_mod", "Underscored ID");
        check(
            !new ModFilter("example.mod", mods).relativeDirectory()
                .equals(new ModFilter("example_mod", mods).relativeDirectory()),
            "Filesystem escaping must not mix two mods' results");
        System.out.println("Mod filter regression checks passed");
    }

    private static void expectInvalid(String input, Map<String, String> mods) {
        try {
            new ModFilter(input, mods);
            throw new AssertionError("Expected invalid input: " + input);
        } catch (IllegalArgumentException expected) {
            // Invalid filters must stop the dump instead of silently exporting everything.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
