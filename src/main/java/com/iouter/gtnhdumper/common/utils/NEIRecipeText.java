package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.iouter.gtnhdumper.common.recipe.NEIDisplayHandlerRecipe;

import codechicken.nei.recipe.TemplateRecipeHandler;

/** 非GT处理器的配方文字。按源码读取业务字段，不运行drawExtras中的OpenGL或屏幕状态逻辑。 */
public final class NEIRecipeText {

    private NEIRecipeText() {}

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        return NEIDisplayHandlerRecipe.field(object, name);
    }

    private static Object call(Object object, String name) throws ReflectiveOperationException {
        Method method = object.getClass()
            .getMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }

    private static String tr(String key, Object... arguments) {
        return StatCollector.translateToLocalFormatted(key, arguments);
    }

    /** 每条记录保留结构化量值和显示说明；失败单独记录，不能将失败当成无文字。 */
    public static void enrich(TemplateRecipeHandler handler, TemplateRecipeHandler.CachedRecipe recipe, int recipeIndex,
        Map<String, Object> data) {
        List<String> lines = new ArrayList<>();
        String source = handler.getClass()
            .getName();
        try {
            switch (source) {
                case "com.science.gtnl.client.nei.NuclearReactorGuideHandler" -> {
                    for (Object line : (List<?>) call(recipe, "getLines")) lines.add((String) line);
                }
                case "alkalus.main.nei.NEI_Handler_Distillery" -> {
                    List<Object> inputs = new ArrayList<>(), outputs = new ArrayList<>();
                    for (String name : new String[] { "ingred1", "ingred2", "jars" }) {
                        Object stack = field(recipe, name);
                        inputs.add(
                            stack == null ? null : RecipeUtil.getRecipeItems((codechicken.nei.PositionedStack) stack));
                    }
                    for (codechicken.nei.PositionedStack stack : (codechicken.nei.PositionedStack[]) field(
                        recipe,
                        "outputs")) outputs.add(stack == null ? null : RecipeUtil.getRecipeItems(stack));
                    data.put("inputItems", inputs);
                    data.put("outputItems", outputs);
                    data.remove("otherItems");
                }

                case "dev.rndmorris.salisarcana.common.compat.nei.WandCapSubstitutionHandler", "dev.rndmorris.salisarcana.common.compat.nei.WandCoreSubstitutionHandler", "com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.WandRecipeHandler" -> {
                    // 替换与法杖合成仅使用本处理器缓存；要素代理物品不能进入固体输入。
                    List<?> ingredients = (List<?>) field(recipe, "ingredients");
                    List<Object> inputs = new ArrayList<>(java.util.Collections.nCopies(9, null));
                    for (Object object : ingredients) {
                        codechicken.nei.PositionedStack stack = (codechicken.nei.PositionedStack) object;
                        int column = (stack.relx - 47) / 28, row = (stack.rely - 38) / 27;
                        if (column < 0 || column > 2 || row < 0 || row > 2)
                            throw new IllegalStateException("Unknown arcane slot");
                        inputs.set(row * 3 + column, RecipeUtil.getRecipeItems(stack));
                    }
                    data.put("inputItems", inputs);
                    data.put("inputAspects", call(recipe, "getAspects"));
                    data.remove("otherItems");
                    java.util.Set<String> research = new java.util.LinkedHashSet<>();
                    for (Object requirement : (List<?>) field(recipe, "prereqs"))
                        research.add((String) field(requirement, "research"));
                    data.put("research", research);
                    data.put("handlerScoped", true);
                }

                case "ic2.neiIntegration.core.recipehandler.CentrifugeRecipeHandler", "ic2.neiIntegration.core.recipehandler.BlockCutterRecipeHandler", "ic2.neiIntegration.core.recipehandler.OreWashingRecipeHandler" -> {
                    NBTTagCompound meta = (NBTTagCompound) field(recipe, "meta");
                    String key = source.endsWith("CentrifugeRecipeHandler") ? "minHeat"
                        : source.endsWith("BlockCutterRecipeHandler") ? "hardness" : "amount";
                    if (meta != null && meta.hasKey(key)) {
                        int value = meta.getInteger(key);
                        data.put(key.equals("amount") ? "waterAmount" : key, value);
                    }
                }
                case "ic2.neiIntegration.core.recipehandler.ScrapboxRecipeHandler" -> {
                    double chance = ((Number) field(recipe, "chance")).doubleValue();
                    data.put("dropChance", chance);
                }
                case "advsolar.client.nei.MTRecipeHandler" -> {
                    data.put("energyEU", field(recipe, "energy"));
                    data.put("inputName", ((ItemStack) field(recipe, "input")).getDisplayName());
                    data.put("outputName", ((ItemStack) field(recipe, "output")).getDisplayName());
                }
                case "buildcraft.compat.nei.RecipeHandlerAssemblyTable", "buildcraft.compat.nei.RecipeHandlerIntegrationTable" -> {
                    data.put("energyRF", field(recipe, "energy"));
                }
                case "binnie.genetics.nei.IncubatorRecipeHandler" -> {
                    // 培养箱按注册顺序加载完整配方；缓存只保留百分比文本，原始配方保留0–1概率。
                    List<?> recipes = new ArrayList<>(
                        (java.util.Collection<?>) Class.forName("binnie.genetics.machine.incubator.Incubator")
                            .getField("RECIPES")
                            .get(null));
                    data.put("lossChance", call(recipes.get(recipeIndex), "getLossChance"));
                }
                case "alkalus.main.nei.NEI_Handler_Cauldron" -> {
                    int power = ((Number) call(recipe, "getPower")).intValue();
                    data.put("altarPower", power);
                }
                case "com.pam.harvestcraft.GardenRecipeHandler" -> {
                    String key = (String) field(recipe, "biomeTransKey");
                    data.put("biomeTranslationKey", key);
                    lines.add(tr("harvestcraft.nei.garden.found", tr(key)));
                }
                case "tconstruct.plugins.nei.RecipeHandlerDryingRack" -> {
                    int ticks = ((Number) field(recipe, "time")).intValue();
                    data.put("duration", ticks);
                }
                case "tonius.neiintegration.mods.railcraft.RecipeHandlerBlastFurnace", "tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven" -> {
                    data.put("duration", field(recipe, "cookTime"));
                }
                case "gtPlusPlus.nei.DecayableRecipeHandler" -> {
                    int ticks = ((Number) field(recipe, "time")).intValue();
                    data.put("duration", ticks);
                }
                case "tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary" -> lines
                    .add((String) field(recipe, "oreName"));
                case "WayofTime.alchemicalWizardry.client.nei.NEIAlchemyRecipeHandler" -> {
                    data.put("lifeEssence", field(recipe, "lp"));
                }
                case "WayofTime.alchemicalWizardry.client.nei.NEICalcinatorHandler" -> {
                    data.put("reagentAmount", field(recipe, "amount"));
                    data.put("reagentName", field(recipe, "nameText"));
                }
                case "com.kuba6000.mobsinfo.nei.VillagerTradesHandler" -> {
                    // 村民交易允许缺少第二输入；按交易结构保留空位，不能把压缩列表两两配对。
                    List<Object> tradeInputs = new ArrayList<>(), tradeOutputs = new ArrayList<>();
                    List<Object> chances = new ArrayList<>();
                    for (Object pair : (List<?>) field(recipe, "tradeList")) {
                        Object sides = call(pair, "getLeft"), inputs = call(sides, "getLeft");
                        tradeInputs
                            .add(RecipeUtil.getRecipeItems((codechicken.nei.PositionedStack) call(inputs, "getLeft")));
                        Object second = call(inputs, "getRight");
                        tradeInputs.add(
                            second == null ? null
                                : RecipeUtil.getRecipeItems((codechicken.nei.PositionedStack) second));
                        tradeOutputs
                            .add(RecipeUtil.getRecipeItems((codechicken.nei.PositionedStack) call(sides, "getRight")));
                        chances.add(call(call(pair, "getRight"), "getChance"));
                    }
                    data.put("inputItems", tradeInputs);
                    data.put("outputItems", tradeOutputs);
                    data.put("tradeSlots", true);
                    data.put("tradeChances", chances);
                    data.remove("otherItems");

                    data.put("profession", field(recipe, "profession"));
                    data.put("professionID", field(recipe, "professionID"));
                    data.put("mod", field(recipe, "mod"));
                }
                case "com.pam.harvestcraft.AnimalTrapRecipeHandler", "com.pam.harvestcraft.FishTrapRecipeHandler" -> {
                    List<?> chances = (List<?>) field(recipe, "chances");
                    List<Object> outputs = new ArrayList<>();
                    List<codechicken.nei.PositionedStack> stacks = recipe.getOtherStacks();
                    for (int i = 0; i < stacks.size(); i++) {
                        int chance = Math.round(((Number) chances.get(i)).floatValue() * 100);
                        outputs.add(RecipeUtil.getRecipeItems(stacks.get(i).items, chance));
                    }
                    data.put("outputItems", outputs);
                    data.remove("otherItems");
                }
                case "ic2.neiIntegration.core.recipehandler.LatheRecipeHandler" -> {
                    ItemStack stack = recipe.getResult().item;
                    Object item = stack.getItem();
                    Method current = item.getClass()
                        .getMethod("getCurrentState", ItemStack.class);
                    Method width = item.getClass()
                        .getMethod("getWidth", ItemStack.class);
                    int[] state = (int[]) current.invoke(item, stack);
                    int maximum = ((Number) width.invoke(item, stack)).intValue();
                    data.put("segmentWidths", state);
                    data.put("maximumWidth", maximum);
                }
                case "tconstruct.plugins.nei.RecipeHandlerToolMaterials" -> toolMaterial(recipe, data);
                default -> {
                    return;
                }
            }
            if (!lines.isEmpty()) data.put("displayInformation", lines);
        } catch (ReflectiveOperationException | RuntimeException error) {
            data.put(
                "displayInformationError",
                error.getClass()
                    .getName() + ": "
                    + error.getMessage());
            RecipeItemVariants.reportFailure(source + " text", error);
        }
    }

    /** 匠魂材料说明包含工具、弓与箭三组参数；反射只读取当前模组的公开材料对象。 */
    private static void toolMaterial(Object recipe, Map<String, Object> data) throws ReflectiveOperationException {
        Object material = field(recipe, "material");
        if (material != null) {
            data.put("materialName", call(material, "localizedName"));
            Number durability = (Number) field(material, "durability");
            Number modifier = (Number) field(material, "handleModifier");
            data.put("durability", durability);
            data.put("handleModifier", modifier);
            data.put("miningSpeed", ((Number) field(material, "miningspeed")).doubleValue() / 100);
            Method level = Class.forName("tconstruct.library.util.HarvestLevels")
                .getMethod("getHarvestLevelName", int.class);
            data.put("harvestLevel", field(material, "harvestLevel"));
            data.put("harvestLevelName", level.invoke(null, ((Number) field(material, "harvestLevel")).intValue()));
            data.put("attackDamage", field(material, "attack"));
            int reinforced = ((Number) field(material, "reinforced")).intValue();
            if (reinforced > 0) data.put("reinforced", reinforced);
            Object ability = call(material, "ability");
            if (ability != null) {
                double stonebound = ((Number) field(material, "stonebound")).doubleValue();
                data.put(
                    "traitDescriptions",
                    java.util.Collections
                        .singletonList(ability + (stonebound == 0 ? "" : " (" + Math.abs(stonebound) + ")")));
            }
        }
        Object bow = field(recipe, "bowMaterial");
        if (bow != null) {
            data.put("drawSpeed", field(bow, "drawspeed"));
            data.put("maxFlightSpeed", field(bow, "flightSpeedMax"));
        }
        Object arrow = field(recipe, "arrowMaterial");
        if (arrow != null) {
            data.put("arrowMass", field(arrow, "mass"));
            data.put("breakChance", field(arrow, "breakChance"));
        }
    }

}
