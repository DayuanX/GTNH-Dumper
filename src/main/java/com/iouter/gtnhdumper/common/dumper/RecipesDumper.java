package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bdew.neiaddons.forestry.BaseBreedingRecipeHandler;
import net.bdew.neiaddons.forestry.BaseProduceRecipeHandler;
import net.minecraft.util.ChatComponentTranslation;

import com.emoniph.witchery.integration.NEICauldronRecipeHandler;
import com.google.common.base.Objects;
import com.google.gson.JsonObject;
import com.gtnewhorizon.cropsnh.compatibility.NEI.NEICropsNHCropHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.AlchemyRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.AspectCombinationHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.ShapedArcaneRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.ShapelessArcaneRecipeHandler;
import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.base.FilteredDataDumper;
import com.iouter.gtnhdumper.common.recipe.AvaExtremeShapedHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.BloodMagicHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.BotaniaHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.CarpenterHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.CropsNHHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.ForestryHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.GTDefaultHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.GTOreVeinHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.GTSmallOreVeinHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.GTUndergroundFluidHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.GasSiphonHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.GeneralHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.MobHandlerFishingRecipe;
import com.iouter.gtnhdumper.common.recipe.MobHandlerInfernalRecipe;
import com.iouter.gtnhdumper.common.recipe.MobHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.NEIDisplayHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.QuestHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.ShapedCraftingHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.SpacePumpModuleHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.TCHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.VendingMachineHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.WitcheryCauldronHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.Utils;
import com.kuba6000.mobsinfo.nei.MobHandler;
import com.kuba6000.mobsinfo.nei.MobHandlerInfernal;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import forestry.factory.recipes.nei.NEIHandlerCarpenter;
import fox.spiteful.avaritia.compat.nei.ExtremeShapedRecipeHandler;
import gregtech.nei.GTNEIDefaultHandler;
import gtneioreplugin.plugin.gregtech5.PluginGT5SmallOreStat;
import gtneioreplugin.plugin.gregtech5.PluginGT5UndergroundFluid;
import gtneioreplugin.plugin.gregtech5.PluginGT5VeinStat;
import gtnhintergalactic.nei.GasSiphonRecipeHandler;
import gtnhintergalactic.nei.SpacePumpModuleRecipeHandler;

public class RecipesDumper extends FilteredDataDumper {

    public RecipesDumper() {
        super("tools.dump.gtnhdumper.recipe");
    }

    private static BaseHandlerRecipe dumpRecipes(IRecipeHandler recipeHandler) {
        final String clazz = Utils.getAfterLastDot(recipeHandler.getHandlerId())
            .replace(".", "_");
        if (CommonProxy.isGTLoaded) {
            if (recipeHandler instanceof PluginGT5VeinStat) {
                return new GTOreVeinHandlerRecipe(recipeHandler);
            }
            if (recipeHandler instanceof PluginGT5SmallOreStat) {
                return new GTSmallOreVeinHandlerRecipe(recipeHandler);
            }
            if (recipeHandler instanceof PluginGT5UndergroundFluid) {
                return new GTUndergroundFluidHandlerRecipe(recipeHandler);
            }
            if (recipeHandler instanceof GasSiphonRecipeHandler) {
                return new GasSiphonHandlerRecipe(recipeHandler);
            }
            if (recipeHandler instanceof SpacePumpModuleRecipeHandler) {
                return new SpacePumpModuleHandlerRecipe(recipeHandler);
            }
            if (recipeHandler instanceof GTNEIDefaultHandler) {
                return new GTDefaultHandlerRecipe((GTNEIDefaultHandler) recipeHandler);
            }
        }
        if (CommonProxy.isTCLoaded) {
            if (recipeHandler.getClass()
                .getName()
                .equals("com.gtnewhorizons.aspectrecipeindex.nei.ItemsContainingAspectHandler"))
                return new com.iouter.gtnhdumper.common.recipe.AspectItemsHandlerRecipe(recipeHandler);

            if (recipeHandler instanceof AspectCombinationHandler
                || recipeHandler.getClass() == ShapedArcaneRecipeHandler.class
                || recipeHandler.getClass() == ShapelessArcaneRecipeHandler.class
                || recipeHandler instanceof AlchemyRecipeHandler
                || recipeHandler.getClass() == InfusionRecipeHandler.class) {
                return new TCHandlerRecipe(recipeHandler);
            }
        }
        if (CommonProxy.isAvaritiaLoaded) {
            if (recipeHandler instanceof ExtremeShapedRecipeHandler) {
                return new AvaExtremeShapedHandlerRecipe(recipeHandler);
            }
        }
        if (CommonProxy.isFRLoaded) {
            if (recipeHandler instanceof NEIHandlerCarpenter) {
                return new CarpenterHandlerRecipe(recipeHandler);
            }
            if (CommonProxy.isNEIAddonLoaded) {
                if (recipeHandler instanceof BaseBreedingRecipeHandler
                    || recipeHandler instanceof BaseProduceRecipeHandler) {
                    return new ForestryHandlerRecipe(recipeHandler);
                }
            }
        }
        if (CommonProxy.isMobsInfoLoaded) {
            if (recipeHandler.getClass()
                .getName()
                .equals("com.kuba6000.mobsinfo.nei.MobHandlerFishing")) {
                return new MobHandlerFishingRecipe(recipeHandler);
            }
            if (recipeHandler instanceof MobHandler) {
                return new MobHandlerRecipe((MobHandler) recipeHandler);
            }
            if (recipeHandler instanceof MobHandlerInfernal) {
                return new MobHandlerInfernalRecipe((MobHandlerInfernal) recipeHandler);
            }
        }
        if (CommonProxy.isWitcheryLoaded && recipeHandler instanceof NEICauldronRecipeHandler) {
            return new WitcheryCauldronHandlerRecipe(recipeHandler);
        }
        if (CommonProxy.isCropsNHLoaded && recipeHandler instanceof NEICropsNHCropHandler cropsNHHandler) {
            return new CropsNHHandlerRecipe(cropsNHHandler);
        }
        if (recipeHandler instanceof TemplateRecipeHandler templateHandler
            && "vendingmachine".equals(templateHandler.getOverlayIdentifier())) {
            return new VendingMachineHandlerRecipe(recipeHandler);
        }
        if (ShapedCraftingHandlerRecipe.supports(recipeHandler.getClass())) {
            return new ShapedCraftingHandlerRecipe(recipeHandler);
        }
        String source = recipeHandler.getClass()
            .getName();
        if (source.startsWith("crazypants.enderio.nei.") || source.equals("codechicken.nei.recipe.FuelRecipeHandler")
            || source.equals("forestry.factory.recipes.nei.NEIHandlerMoistener")
            || source.equals("forestry.factory.recipes.nei.NEIHandlerSqueezer")
            || source.equals("forestry.factory.recipes.nei.NEIHandlerBottler")
            || source.equals("forestry.factory.recipes.nei.NEIHandlerFabricator")
            || source.equals("thaumic.tinkerer.client.nei.NEIInfusedSeedRecipeHandler")
            || source.equals("buildcraft.compat.nei.RecipeHandlerRefinery")
            || source.equals("eu.usrv.enhancedlootbags.integration.nei.LootBagRecipeHandler")
            || source.equals("com.gtnewhorizon.cropsnh.compatibility.NEI.NEICropsNHCropstickBreedingHandler")
            || source.equals("com.gtnewhorizon.cropsnh.compatibility.NEI.NEICropsNHMutationPoolHandler")) {
            return new NEIDisplayHandlerRecipe(recipeHandler);
        }
        if (source.equals("vazkii.botania.client.integration.nei.recipe.RecipeHandlerBrewery")
            || source.equals("vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool")
            || source.equals("vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar")
            || source.equals("vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania")) {
            return new BotaniaHandlerRecipe(recipeHandler);
        }
        if (source.equals("bq_standard.integration.nei.QuestRecipeHandler"))
            return new QuestHandlerRecipe(recipeHandler);
        if (source.equals("WayofTime.alchemicalWizardry.client.nei.NEIAltarRecipeHandler")
            || source.equals("WayofTime.alchemicalWizardry.client.nei.NEIMeteorRecipeHandler")) {
            return new BloodMagicHandlerRecipe(recipeHandler);
        }
        return new GeneralHandlerRecipe(recipeHandler);
    }

    /** Visit all adapters without writing recipe files; item ownership is filtered afterwards. */
    public static void collectItemVariants() {
        // GT流体展示物品可能未出现在NEI列表中；与FluidsDumper按同一注册ID收集。
        // 补齐现有物品图标渠道，不创建独立的流体纹理导出。
        if (com.iouter.gtnhdumper.CommonProxy.isGTLoaded) {
            net.minecraft.item.Item displayItem = gregtech.api.enums.ItemList.Display_Fluid.getItem();
            java.util.Set<Integer> visibleFluidIds = new java.util.HashSet<>();
            for (net.minecraft.item.ItemStack stack : codechicken.nei.ItemList.items) {
                if (stack.getItem() == displayItem)
                    visibleFluidIds.add(codechicken.lib.inventory.InventoryUtils.actualDamage(stack));
            }
            for (net.minecraftforge.fluids.Fluid fluid : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids()
                .values()) {
                if (visibleFluidIds.contains(fluid.getID())) continue;
                RecipeItemVariants.capture(new net.minecraft.item.ItemStack(displayItem, 1, fluid.getID()));
            }
        }
        for (IRecipeHandler handler : GuiUsageRecipe.usagehandlers) {
            // NEI initializes lazy caches (including furnace fuels) when creating a query instance.
            if (handler instanceof TemplateRecipeHandler prototype)
                handler = com.iouter.gtnhdumper.common.utils.NEIHandlerInstances.create(prototype);
            TemplateRecipeHandler template = handler instanceof TemplateRecipeHandler ? (TemplateRecipeHandler) handler
                : null;
            List<TemplateRecipeHandler.CachedRecipe> previous = template == null ? null
                : new ArrayList<>(template.arecipes);
            try {
                dumpRecipes(handler).build();
            } catch (Exception | LinkageError e) {
                RecipeItemVariants.reportFailure(
                    handler.getClass()
                        .getName(),
                    e);
            } finally {
                if (template != null) {
                    int recipeIndex = 0;
                    for (TemplateRecipeHandler.CachedRecipe recipe : template.arecipes) {
                        RecipeItemVariants.captureCachedRecipe(
                            recipe,
                            handler.getClass()
                                .getName() + " #"
                                + recipeIndex++);
                    }
                    template.arecipes.clear();
                    template.arecipes.addAll(previous);
                }
            }
        }
        if (CommonProxy.isKubaTechLoaded) {
            try {
                EECRecipeDumper.build();
            } catch (Exception | LinkageError e) {
                RecipeItemVariants.reportFailure(EECRecipeDumper.RECIPE_NAME, e);
            }
        }
    }

    @Override
    public String[] header() {
        return new String[] { "Recipe json" };
    }

    @Override
    public Iterable<String[]> dump(int mode) {
        List<String[]> recipesList = new ArrayList<>();
        ModFilter filter = ModFilter.current();
        Map<String, String> skipped = new LinkedHashMap<>();
        for (IRecipeHandler handler : GuiUsageRecipe.usagehandlers) {
            if (handler instanceof TemplateRecipeHandler prototype)
                handler = com.iouter.gtnhdumper.common.utils.NEIHandlerInstances.create(prototype);
            final String name = handler.getRecipeName();
            if (filter.isActive() && !(CommonProxy.isGTLoaded && handler instanceof GTNEIDefaultHandler)) {
                skipped.put(handler.getHandlerId(), "Original registering mod is not recorded by this handler");
                continue;
            }
            final String handlerName = handler.getHandlerId();
            final String handlerId = Objects
                .firstNonNull(handler instanceof TemplateRecipeHandler ? handler.getOverlayIdentifier() : null, "null");
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handlerName, handlerId);
            String modID = info != null ? info.getModId() : "Unknown";
            String id = Utils.getAfterLastDot(handlerId);
            if (java.util.Objects.equals(id, "name") && handlerId != null) {
                id = Utils.getAfterLastDot(handlerId.substring(0, handlerId.length() - ".name".length()));
            }
            String clazz = Utils.getAfterLastDot(handlerName);
            String fileName = "recipes/" + modID + "/" + clazz + "_" + id + ".json";
            fileName = Utils.replacePathIllegalChars(fileName);
            File file = filter.outputFile(fileName);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                JsonObject data = dumpRecipes(handler).build();
                if (data.has("sourceFilterUnsupported")) {
                    skipped.put(handlerName, "Custom recipe storage does not retain original owners");
                } else if (data.has("recipesWithoutSource") && data.get("recipesWithoutSource")
                    .getAsLong() > 0) {
                        skipped.put(
                            handlerName,
                            data.get("recipesWithoutSource")
                                .getAsLong()
                                + " recipes have no original owner; enable GregTech NEIRecipeOwner and restart before dumping");
                    }
                GTNHDumper.GSON.toJson(data, writer);
                recipesList.add(new String[] { name });
                GTNHDumper.info("已写入：" + file.getAbsolutePath());
            } catch (IOException e) {
                file.deleteOnExit();
                GTNHDumper.LOG.error(e);
            } catch (Exception e) {
                GTNHDumper.info("导出" + name + "时发生错误：" + e.getLocalizedMessage());
                file.deleteOnExit();
                GTNHDumper.LOG.error(e);
            }
        }
        if (!filter.isActive() && CommonProxy.isKubaTechLoaded) try {
            EECRecipeDumper.dump();
            recipesList.add(new String[] { EECRecipeDumper.RECIPE_NAME });
        } catch (Exception e) {
            GTNHDumper.info("导出" + EECRecipeDumper.RECIPE_NAME + "时发生错误：" + e.getLocalizedMessage());
            GTNHDumper.LOG.error(e);
        }
        if (filter.isActive()) {
            if (CommonProxy.isKubaTechLoaded) {
                skipped.put("kubatech.eec", "Original registering mod is not recorded by this custom recipe storage");
            }
            File report = filter.outputFile("recipes_filter_report.json");
            try (FileWriter writer = new FileWriter(report)) {
                GTNHDumper.GSON.toJson(skipped, writer);
            } catch (IOException e) {
                GTNHDumper.LOG.error("Could not write recipe source filter report", e);
            }
            if (!skipped.isEmpty()) {
                NEIClientUtils.printChatMessage(
                    new ChatComponentTranslation(
                        "nei.options.tools.dump.gtnhdumper.modFilter.skipped",
                        skipped.size(),
                        report.getPath()));
            }
        }
        return recipesList;
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation("nei.options.tools.dump.gtnhdumper.recipes.dumped", file.getPath());
    }

    @Override
    public int modeCount() {
        return 1;
    }
}
