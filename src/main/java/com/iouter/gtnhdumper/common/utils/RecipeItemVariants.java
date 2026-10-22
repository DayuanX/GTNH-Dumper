package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.iouter.gtnhdumper.Tags;
import com.iouter.gtnhdumper.common.dumper.RecipesDumper;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Lazy, client-session snapshot of concrete stacks visited by the existing recipe adapters. */
public final class RecipeItemVariants {

    private static final Logger LOG = LogManager.getLogger(Tags.MODID);
    private static final ThreadLocal<RecipeItemVariants> ACTIVE = new ThreadLocal<>();
    private static List<ItemStack> cached;
    private static int cachedFailures;
    private static Map<String, String> lastFailures = new LinkedHashMap<>();
    private final Map<Key, ItemStack> stacks = new LinkedHashMap<>();
    private int failures;
    private final Map<String, String> failureDetails = new LinkedHashMap<>();

    private RecipeItemVariants() {}

    public static boolean isCollecting() {
        return ACTIVE.get() != null;
    }

    public static void capture(ItemStack stack) {
        capture(stack, true);
    }

    /** GT产物是实际复制出的物品栈；metadata为32767时仍保留该精确身份。 */
    public static void captureExactOutput(ItemStack stack) {
        capture(stack, false);
    }

    private static void capture(ItemStack stack, boolean expandWildcard) {
        RecipeItemVariants collector = ACTIVE.get();
        if (collector == null || stack == null || stack.getItem() == null || stack.stackSize < 0) return;
        try {
            // A wildcard is a matching rule. Render concrete NEI subtypes with the
            // recipe tag preserved, including tags absent from creative inventory.
            if (expandWildcard && codechicken.lib.inventory.InventoryUtils.actualDamage(stack)
                == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) {
                boolean found = false;
                List<ItemStack> candidates = new ArrayList<>();
                for (ItemStack candidate : codechicken.nei.ItemList.items) {
                    if (candidate.getItem() == stack.getItem()) candidates.add(candidate);
                }
                // NEI can hide valid registered items; ask their creative enumeration
                // for real metadata values rather than inventing a zero-damage subtype.
                if (candidates.isEmpty()) stack.getItem()
                    .getSubItems(stack.getItem(), net.minecraft.creativetab.CreativeTabs.tabAllSearch, candidates);
                for (ItemStack candidate : candidates) {
                    if (candidate.getItem() != stack.getItem()
                        || codechicken.lib.inventory.InventoryUtils.actualDamage(candidate)
                            == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
                        continue;
                    ItemStack concrete = candidate.copy();
                    if (stack.hasTagCompound()) concrete.setTagCompound(
                        (NBTTagCompound) stack.getTagCompound()
                            .copy());
                    capture(concrete);
                    found = true;
                }
                if (!found)
                    reportFailure("wildcard item", new IllegalStateException("No concrete NEI subtype: " + stack));
                return;
            }
            Key key = new Key(stack);
            if (!collector.stacks.containsKey(key)) {
                ItemStack copy = stack.copy();
                copy.stackSize = 1;
                collector.stacks.put(key, copy);
            }
        } catch (Exception | LinkageError e) {
            reportFailure("item stack", e);
        }
    }

    public static void capture(PositionedStack positioned) {
        if (positioned == null || positioned.items == null) return;
        for (ItemStack stack : positioned.items) capture(stack);
    }

    public static void captureCachedRecipe(TemplateRecipeHandler.CachedRecipe recipe, String context) {
        if (recipe == null) {
            reportFailure(context, new IllegalArgumentException("Null cached recipe"));
            return;
        }
        captureRecipeSlots(recipe::getIngredients, recipe::getResult, recipe::getOtherStacks, context);
    }

    private static void captureRecipeSlots(Supplier<List<PositionedStack>> input, Supplier<PositionedStack> output,
        Supplier<List<PositionedStack>> other, String context) {
        try {
            List<PositionedStack> ingredients = input.get();
            if (ingredients != null) for (PositionedStack stack : ingredients) capture(stack);
        } catch (Exception | LinkageError e) {
            reportFailure(context + " ingredients", e);
        }
        try {
            capture(output.get());
        } catch (Exception | LinkageError e) {
            reportFailure(context + " result", e);
        }
        try {
            List<PositionedStack> others = other.get();
            if (others != null) for (PositionedStack stack : others) capture(stack);
        } catch (Exception | LinkageError e) {
            reportFailure(context + " other stacks", e);
        }
    }

    public static void reportFailure(String context, Throwable error) {
        RecipeItemVariants collector = ACTIVE.get();
        if (collector != null) collector.failures++;
        if (collector != null) collector.failureDetails.put(context.replaceAll(" #\\d+", " #N"), error.toString());
        // Keep the failure count exact while bounding logs for handlers with thousands of broken recipes.
        if (collector == null || collector.failures <= 30) {
            LOG.error("Recipe item enumeration failed: " + context, error);
        } else if (collector.failures == 31) {
            LOG.error("Further recipe item enumeration errors suppressed; scan remains incomplete");
        }
    }

    public static List<ItemStack> get() {
        return get(RecipesDumper::collectItemVariants);
    }

    private static synchronized List<ItemStack> get(Runnable scan) {
        if (cached != null) return cached;
        RecipeItemVariants collector = new RecipeItemVariants();
        List<ItemStack> result;
        ACTIVE.set(collector);
        try {
            scan.run();
            result = new ArrayList<>(collector.stacks.values());
            cachedFailures = collector.failures;
            lastFailures = new LinkedHashMap<>(collector.failureDetails);
            // A transient adapter failure must not poison every later export in this session.
            if (collector.failures == 0) cached = result;
        } finally {
            ACTIVE.remove();
        }
        LOG.info(
            "Recipe item scan: " + result.size()
                + " variants; "
                + collector.failures
                + " failures (see log). Only variants enumerable by installed recipe adapters are included.");
        return result;
    }

    public static boolean isComplete() {
        return cached != null;
    }

    public static Map<String, String> failureDetails() {
        return new LinkedHashMap<>(lastFailures);
    }

    public static void warnIfIncomplete() {
        if (cachedFailures > 0) {
            NEIClientUtils.printChatMessage(
                new ChatComponentText(
                    "Recipe item scan incomplete: " + cachedFailures
                        + " failures. See log for affected recipes/handlers; the next item/icon export will retry."));
        }
    }

    /** Structural NBT equality, independent of count, text ordering and short-key hashes. */
    public static final class Key {

        private final Item item;
        private final int damage;
        private final NBTTagCompound tag;

        public Key(ItemStack stack) {
            item = stack.getItem();
            damage = codechicken.lib.inventory.InventoryUtils.actualDamage(stack);
            tag = stack.stackTagCompound == null ? null : (NBTTagCompound) stack.stackTagCompound.copy();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key key)) return false;
            return item == key.item && damage == key.damage && Objects.equals(tag, key.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(item), damage, tag);
        }
    }
}
