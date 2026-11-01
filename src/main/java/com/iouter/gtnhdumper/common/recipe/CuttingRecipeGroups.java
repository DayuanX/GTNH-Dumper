package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;

import gregtech.api.enums.GTValues;
import gregtech.api.objects.SubstituteFluidStack;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.loaders.postload.recipes.FakeCuttingSpecialInfo;

/** 板材切割机：沿用 NEI 的物品分组和流体轮播规则，按原始配方来源拆分。 */
final class CuttingRecipeGroups {

    private CuttingRecipeGroups() {}

    static Collection<GTRecipe> getRecipes(RecipeCategory category) {
        Map<RecipeKey, List<GTRecipe>> groups = new LinkedHashMap<>();
        for (GTRecipe recipe : RecipeMaps.cutterRecipes.getBackend()
            .getAllRecipes()) {
            if (!recipe.mEnabled || recipe.mHidden || recipe.mInputs == null || recipe.mOutputs == null) continue;
            RecipeKey key = new RecipeKey(
                GTDefaultHandlerRecipe.originalOwner(recipe),
                Arrays.stream(recipe.mInputs)
                    .map(stack -> GTUtility.persistentHash(stack, true, true))
                    .collect(Collectors.toList()),
                Arrays.stream(recipe.mOutputs)
                    .map(stack -> GTUtility.persistentHash(stack, true, true))
                    .collect(Collectors.toList()));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(recipe);
        }

        List<GTRecipe> result = new ArrayList<>();
        for (List<GTRecipe> group : groups.values()) {
            GTRecipe template = group.get(0);
            List<FluidStack> fluids = new ArrayList<>();
            FakeCuttingSpecialInfo specialInfo = new FakeCuttingSpecialInfo();
            GTRecipeBuilder builder = GTValues.RA.stdBuilder()
                .itemInputs(template.mInputs)
                .itemOutputs(template.mOutputs)
                .eut(template.mEUt)
                .duration(template.mDuration)
                .special(specialInfo)
                .recipeCategory(category)
                .fake();
            for (GTRecipe recipe : group) {
                mergeFluids(recipe, fluids, specialInfo);
                mergeMetadata(recipe, builder);
            }
            if (!fluids.isEmpty()) {
                builder.fluidInputs(new SubstituteFluidStack(fluids.toArray(new FluidStack[0])));
            }
            // 构造独立的导出记录，保留原始归属；游戏配方池保持原状。
            GTRecipe merged = (fluids.isEmpty() ? builder.build() : builder.buildWithAlt())
                .orElseThrow(() -> new IllegalStateException("Invalid cutting recipe group"));
            merged.owners = template.owners == null ? null : new ArrayList<>(template.owners);
            result.add(merged);
        }
        return result;
    }

    /** 切削液按原始配方顺序轮播；与 NEI 一致，按流体、用量和 NBT 去重。 */
    private static void mergeFluids(GTRecipe recipe, List<FluidStack> fluids, FakeCuttingSpecialInfo specialInfo) {
        List<FluidStack> candidates = new ArrayList<>();
        if (recipe.mFluidInputs.length > 0 && recipe.mFluidInputs[0] != null) candidates.add(recipe.mFluidInputs[0]);
        if (recipe.mAltFluidInputs != null) {
            for (FluidStack[] alternatives : recipe.mAltFluidInputs) Collections.addAll(candidates, alternatives);
        }
        for (FluidStack candidate : candidates) {
            if (fluids.stream()
                .anyMatch(fluid -> fluid.isFluidStackIdentical(candidate))) continue;
            fluids.add(candidate);
            specialInfo.add(candidate, recipe.mDuration);
        }
    }

    /** 沿用 NEI 的元数据合并顺序，同名字段采用组内后续配方的值。 */
    @SuppressWarnings("unchecked")
    private static void mergeMetadata(GTRecipe recipe, GTRecipeBuilder builder) {
        for (Map.Entry<RecipeMetadataKey<?>, Object> entry : recipe.getMetadataStorage()
            .getEntries()) {
            builder.metadata((RecipeMetadataKey<Object>) entry.getKey(), entry.getValue());
        }
    }

    @Desugar
    private record RecipeKey(String sourceMod, List<Integer> inputs, List<Integer> outputs) {}
}
