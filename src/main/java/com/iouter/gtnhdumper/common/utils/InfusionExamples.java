package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;

/** 神秘注魔的NBT修改示例：选择一个有效中央输入，保留输入状态后替换指定顶层标签。 */
public final class InfusionExamples {

    private InfusionExamples() {}

    private static boolean isWandAugmentation(Object recipe) {
        return recipe.getClass()
            .getName()
            .equals("jcm2606.thaumicmachina.core.crafting.infusion.RecipeInfusionWandAugmentation");
    }

    public static boolean hasCustomOutput(Object recipe) {
        String name = recipe.getClass()
            .getName();
        return isWandAugmentation(recipe) || name.equals("tb.utils.RevolverInfusionRecipe");
    }

    /** 法杖需要先具备前置增幅；用模组自己的API构造示例，保留杖柄、杖端等标签。 */
    public static ItemStack prepareCustomInput(Object recipe, ItemStack input) throws ReflectiveOperationException {
        if (!isWandAugmentation(recipe)) return input;
        Object augmentation = recipe.getClass()
            .getField("augmentation")
            .get(recipe);
        Object prerequisites = augmentation.getClass()
            .getMethod("getPrerequisiteAugmentations")
            .invoke(augmentation);
        if (prerequisites == null) return input;
        Class<?> helper = Class.forName("jcm2606.thaumicmachina.wand.WandHelper");
        return (ItemStack) helper
            .getMethod("addAugmentationsTo", ItemStack.class, prerequisites.getClass(), boolean.class)
            .invoke(null, input, prerequisites, true);
    }

    /** 自定义配方的追加/升级语义，与标准替换顶层NBT的patch分开保存。 */
    public static Map<String, Object> customRule(Object recipe) throws ReflectiveOperationException {
        if (isWandAugmentation(recipe)) return wandAugmentationRule(recipe);
        if (recipe.getClass()
            .getName()
            .equals("tb.utils.RevolverInfusionRecipe")) return revolverUpgradeRule(recipe);
        throw new IllegalArgumentException(
            "Unsupported custom infusion recipe: " + recipe.getClass()
                .getName());
    }

    private static Map<String, Object> wandAugmentationRule(Object recipe) throws ReflectiveOperationException {
        Map<String, Object> rule = new LinkedHashMap<>();
        Object augmentation = recipe.getClass()
            .getField("augmentation")
            .get(recipe);
        rule.put("kind", "appendWandAugmentation");
        rule.put(
            "augmentation",
            augmentation.getClass()
                .getMethod("getID")
                .invoke(augmentation));
        List<Object> prerequisites = new ArrayList<>();
        Object[] required = (Object[]) augmentation.getClass()
            .getMethod("getPrerequisiteAugmentations")
            .invoke(augmentation);
        if (required != null) {
            for (Object value : required) {
                prerequisites.add(
                    value.getClass()
                        .getMethod("getID")
                        .invoke(value));
            }
        }
        rule.put("requiredAugmentations", prerequisites);
        rule.put("rejectExistingAugmentation", true);
        return rule;
    }

    private static Map<String, Object> revolverUpgradeRule(Object recipe) throws ReflectiveOperationException {
        Map<String, Object> rule = new LinkedHashMap<>();
        java.lang.reflect.Field field = recipe.getClass()
            .getDeclaredField("upgrade");
        field.setAccessible(true);
        Object upgrade = field.get(recipe);
        Class<?> type = upgrade.getClass();
        rule.put("kind", "incrementRevolverUpgrade");
        rule.put(
            "upgradeId",
            type.getField("id")
                .get(upgrade));
        rule.put(
            "upgradeName",
            type.getField("text_id")
                .get(upgrade));
        rule.put(
            "maxLevel",
            type.getField("maxLevel")
                .get(upgrade));
        rule.put(
            "instabilityPerLevel",
            type.getField("instability")
                .get(upgrade));
        rule.put("aspectMultiplierPerLevel", 2);
        List<Object> conflicts = new ArrayList<>();
        for (Object conflict : (List<?>) type.getField("conflicts")
            .get(upgrade)) {
            conflicts.add(
                type.getField("id")
                    .get(conflict));
        }
        rule.put("conflictingUpgradeIds", conflicts);
        return rule;
    }

    public static ItemStack choose(List<ItemStack> representatives, List<ItemStack> available,
        Predicate<ItemStack> matches) {
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack representative : representatives) {
            if (codechicken.lib.inventory.InventoryUtils.actualDamage(representative) != 32767) {
                candidates.add(representative);
            } else {
                for (ItemStack candidate : available) {
                    if (candidate.getItem() == representative.getItem()) candidates.add(candidate);
                }
            }
        }
        // 无额外NBT优先，其次按真实metadata、NBT内容排序，选择不依赖NEI轮播状态。
        return candidates.stream()
            .filter(stack -> codechicken.lib.inventory.InventoryUtils.actualDamage(stack) != 32767)
            .filter(matches)
            .sorted(
                Comparator.comparing(ItemStack::hasTagCompound)
                    .thenComparingInt(codechicken.lib.inventory.InventoryUtils::actualDamage)
                    .thenComparing(
                        stack -> stack.hasTagCompound() ? stack.getTagCompound()
                            .toString() : ""))
            .findFirst()
            .map(stack -> {
                ItemStack copy = stack.copy();
                copy.stackSize = 1;
                return copy;
            })
            .orElseThrow(() -> new IllegalStateException("No valid concrete infusion example"));
    }

    public static ItemStack apply(ItemStack input, String tagName, NBTBase value) {
        ItemStack output = input.copy();
        output.setTagInfo(tagName, value.copy());
        return output;
    }
}
