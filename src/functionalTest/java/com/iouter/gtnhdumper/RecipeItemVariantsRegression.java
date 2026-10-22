package com.iouter.gtnhdumper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;

import codechicken.nei.PositionedStack;

/** Tests real Minecraft stack copies and structural NBT identity without launching a client. */
public final class RecipeItemVariantsRegression {

    private RecipeItemVariantsRegression() {}

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        // InventoryUtils uses the vanilla diamond accessor; this fixture has no Forge registry bootstrap.
        Class.forName("net.minecraft.init.Items");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Field diamond = net.minecraft.init.Items.class.getDeclaredField("diamond");
        unsafe.putObject(unsafe.staticFieldBase(diamond), unsafe.staticFieldOffset(diamond), new Item());
        Constructor<RecipeItemVariants> constructor = RecipeItemVariants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RecipeItemVariants collector = constructor.newInstance();
        Field activeField = RecipeItemVariants.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        ThreadLocal<RecipeItemVariants> active = (ThreadLocal<RecipeItemVariants>) activeField.get(null);
        Field stacksField = RecipeItemVariants.class.getDeclaredField("stacks");
        stacksField.setAccessible(true);
        Map<?, ItemStack> stacks = (Map<?, ItemStack>) stacksField.get(collector);
        ItemStack original = new ItemStack(new Item() {

            @Override
            public void getSubItems(Item item, net.minecraft.creativetab.CreativeTabs tab, List values) {}
        }, 64, 7);
        original.stackTagCompound = new NBTTagCompound();
        original.stackTagCompound.setString("variant", "first");
        ItemStack patched = com.iouter.gtnhdumper.common.utils.InfusionExamples
            .apply(original, "goggles", new net.minecraft.nbt.NBTTagByte((byte) 1));
        check(
            patched.getItemDamage() == 7 && patched.getTagCompound()
                .getString("variant")
                .equals("first"),
            "Infusion output must preserve metadata and unrelated NBT");
        check(
            patched.getTagCompound()
                .getByte("goggles") == 1
                && !original.getTagCompound()
                    .hasKey("goggles"),
            "Infusion output must not mutate the input");
        ItemStack plain = new ItemStack(original.getItem(), 1, 2);
        ItemStack pattern = new ItemStack(original.getItem(), 1, 32767);
        ItemStack example = com.iouter.gtnhdumper.common.utils.InfusionExamples
            .choose(java.util.Arrays.asList(pattern), java.util.Arrays.asList(original, plain), candidate -> true);
        check(
            example.getItemDamage() == 2 && !example.hasTagCompound() && example != plain,
            "Infusion example prefers a concrete plain input without mutating candidates");
        RecipeItemVariants.capture(original);
        check(stacks.isEmpty(), "Normal recipe exports must not collect or trigger scans");
        active.set(collector);
        try {
            RecipeItemVariants.capture((ItemStack) null);
            ItemStack wildcard = original.copy();
            wildcard.setItemDamage(32767);
            RecipeItemVariants.capture(wildcard);
            check(stacks.isEmpty(), "Wildcard recipe patterns must not become rendered item variants");
            RecipeItemVariants.captureExactOutput(wildcard);
            check(
                stacks.size() == 1 && stacks.values()
                    .iterator()
                    .next()
                    .getItemDamage() == 32767,
                "Literal GT outputs must retain metadata 32767 instead of expanding input alternatives");
            stacks.clear();
            RecipeItemVariants.capture(original);
            ItemStack same = original.copy();
            same.stackSize = 0;
            RecipeItemVariants.capture(same);
            check(stacks.size() == 1, "Counts including zero-sized catalysts must not create variants");
            ItemStack saved = stacks.values()
                .iterator()
                .next();
            check(saved != original && saved.stackSize == 1, "Collector must own normalized copies");
            original.stackTagCompound.setString("variant", "second");
            check(
                saved.stackTagCompound.getString("variant")
                    .equals("first"),
                "NBT must be deep copied");
            RecipeItemVariants.capture(original);
            same.setItemDamage(8);
            RecipeItemVariants.capture(same);
            check(stacks.size() == 3, "Distinct NBT and metadata must survive");
            ItemStack candidate = saved.copy();
            candidate.setItemDamage(42);
            candidate.stackSize = 11;
            PositionedStack positioned = new PositionedStack(saved, 0, 0, false);
            positioned.items = new ItemStack[] { saved, null, candidate };
            RecipeItemVariants.capture(positioned);
            check(stacks.size() == 4, "Every candidate must be captured, not only the displayed item");
            check(candidate.stackSize == 11, "Candidate must remain unchanged");
            ItemStack output = saved.copy();
            output.setItemDamage(43);
            Method slots = RecipeItemVariants.class
                .getDeclaredMethod("captureRecipeSlots", Supplier.class, Supplier.class, Supplier.class, String.class);
            slots.setAccessible(true);
            Supplier<List<PositionedStack>> brokenInput = () -> {
                throw new IllegalStateException("Expected regression-test failure");
            };
            Supplier<PositionedStack> validOutput = () -> new PositionedStack(output, 0, 0, false);
            Supplier<List<PositionedStack>> emptyOther = Collections::emptyList;
            slots.invoke(null, brokenInput, validOutput, emptyOther, "regression fixture");
            check(stacks.size() == 5, "An unreadable ingredient must not hide the result");
            Field failuresField = RecipeItemVariants.class.getDeclaredField("failures");
            failuresField.setAccessible(true);
            check(failuresField.getInt(collector) == 2, "Incomplete scans must count failures");

            ItemStack subtype = saved.copy();
            subtype.setItemDamage(99);
            subtype.stackTagCompound.setString("variant", "creative");
            codechicken.nei.ItemList.items.add(subtype);
            try {
                RecipeItemVariants.capture(wildcard);
                ItemStack expanded = stacks.values()
                    .stream()
                    .filter(value -> value.getItemDamage() == 99)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing wildcard candidate"));
                check(
                    expanded.stackTagCompound.getString("variant")
                        .equals("first"),
                    "Wildcard candidates must preserve recipe NBT");
                check(
                    subtype.stackTagCompound.getString("variant")
                        .equals("creative"),
                    "Wildcard expansion must not mutate NEI items");
            } finally {
                codechicken.nei.ItemList.items.remove(subtype);
            }

            same = saved.copy();
            same.stackTagCompound = new NBTTagCompound();
            same.stackTagCompound.setInteger("b", 2);
            same.stackTagCompound.setInteger("a", 1);
            RecipeItemVariants.Key key = new RecipeItemVariants.Key(same);
            same.stackTagCompound = new NBTTagCompound();
            same.stackTagCompound.setInteger("a", 1);
            same.stackTagCompound.setInteger("b", 2);
            RecipeItemVariants.Key reordered = new RecipeItemVariants.Key(same);
            check(
                key.equals(reordered) && key.hashCode() == reordered.hashCode(),
                "NBT order must not duplicate variants");
            same.stackTagCompound.setInteger("a", 3);
            check(key.equals(reordered), "Keys must not change when source NBT changes");
            check(!key.equals(new RecipeItemVariants.Key(same)), "NBT value changes must remain distinct");
        } finally {
            active.remove();
        }
        check(!RecipeItemVariants.isCollecting(), "Collection scope must end");
        Item displayDamageItem = new Item() {

            @Override
            public int getDamage(ItemStack stack) {
                return 10000;
            }
        };
        check(
            !new RecipeItemVariants.Key(new ItemStack(displayDamageItem, 1, 0))
                .equals(new RecipeItemVariants.Key(new ItemStack(displayDamageItem, 1, 5))),
            "Displayed durability must not collapse distinct raw metadata identities");

        Method get = RecipeItemVariants.class.getDeclaredMethod("get", Runnable.class);
        get.setAccessible(true);
        int[] scans = { 0 };
        Runnable incomplete = () -> {
            scans[0]++;
            RecipeItemVariants.capture(original);
            RecipeItemVariants.reportFailure("expected failed scan", new IllegalStateException("fixture"));
        };
        List<ItemStack> partial = (List<ItemStack>) get.invoke(null, incomplete);
        check(partial.size() == 1 && !RecipeItemVariants.isComplete(), "Partial results must not poison the cache");
        Runnable complete = () -> {
            scans[0]++;
            RecipeItemVariants.capture(original);
        };
        List<ItemStack> completeResult = (List<ItemStack>) get.invoke(null, complete);
        check(scans[0] == 2 && RecipeItemVariants.isComplete(), "The next export must retry failed scans");
        check(
            get.invoke(null, complete) == completeResult && scans[0] == 2,
            "Successful scans must be shared across item/icon exports");
        check(!RecipeItemVariants.isCollecting(), "Retry must release collection scope");
        System.out.println("Recipe item variant regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
