package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.iouter.gtnhdumper.common.dumper.NEIFluidTankDumper;

import codechicken.nei.recipe.TemplateRecipeHandler;

/** 与已核对的NEI储罐坐标对应；从同一配方缓存读取，不跨导出轮次按索引拼接。 */
public final class RecipeTankData {

    // 每行依次为 x、y、带方向的槽号：正数为输入，负数为输出，绝对值从 1 开始。
    private static final Map<String, int[][]> POSITIONS = new HashMap<>();
    static {
        POSITIONS.put("forestry.factory.recipes.nei.NEIHandlerStill", new int[][] { { 30, 4, 1 }, { 120, 4, -1 } });
        POSITIONS.put("forestry.factory.recipes.nei.NEIHandlerFermenter", new int[][] { { 30, 4, 1 }, { 120, 4, -1 } });
        POSITIONS.put(
            "buildcraft.compat.nei.RecipeHandlerRefinery",
            new int[][] { { 33, 23, 1 }, { 121, 23, 2 }, { 77, 23, -1 } });
        POSITIONS.put("binnie.genetics.nei.GenepoolRecipeHandler", new int[][] { { 38, 6, 1 }, { 119, 6, -1 } });
        POSITIONS.put("tconstruct.plugins.nei.RecipeHandlerMelting", new int[][] { { 115, 20, -1 } });
        POSITIONS.put("binnie.genetics.nei.IsolatorRecipeHandler", new int[][] { { 40, 6, 1 } });
        POSITIONS.put("binnie.genetics.nei.PolymeriserRecipeHandler", new int[][] { { 27, 6, 1 }, { 48, 6, 2 } });
        POSITIONS.put("binnie.genetics.nei.InoculatorRecipeHandler", new int[][] { { 28, 6, 1 } });
        POSITIONS.put("binnie.genetics.nei.IncubatorRecipeHandler", new int[][] { { 28, 6, 1 }, { 130, 6, -1 } });
        POSITIONS.put("binnie.extratrees.nei.NEIHandlerLumbermill", new int[][] { { 28, 6, 1 } });
        POSITIONS.put("forestry.factory.recipes.nei.NEIHandlerBottler", new int[][] { { 48, 6, 1 } });
        POSITIONS.put("forestry.factory.recipes.nei.NEIHandlerSqueezer", new int[][] { { 117, 7, -1 } });
        POSITIONS.put("tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven", new int[][] { { 95, 13, -1 } });
        POSITIONS.put("forestry.factory.recipes.nei.NEIHandlerCarpenter", new int[][] { { 145, 3, 1 } });
        POSITIONS.put("forestry.factory.recipes.nei.NEIHandlerFabricator", new int[][] { { 21, 37, 1 } });
        POSITIONS.put("forestry.factory.recipes.nei.NEIHandlerMoistener", new int[][] { { 11, 5, 1 } });
        POSITIONS.put("net.bdew.gendustry.nei.MutagenProducerHandler", new int[][] { { 147, 6, -1 } });
        POSITIONS.put("net.bdew.gendustry.nei.LiquifierHandler", new int[][] { { 147, 6, -1 } });
        POSITIONS.put("net.bdew.gendustry.nei.ExtractorHandler", new int[][] { { 147, 6, -1 } });
        POSITIONS.put("net.bdew.gendustry.nei.MutatronHandler", new int[][] { { 27, 6, 1 } });
        POSITIONS.put("net.bdew.gendustry.nei.ReplicatorHandler", new int[][] { { 27, 6, 1 }, { 51, 6, 2 } });
        POSITIONS.put("crazypants.enderio.nei.VatRecipeHandler", new int[][] { { 25, 1, 1 }, { 127, 1, -1 } });
        POSITIONS.put(
            "ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler",
            new int[][] { { 38, 50, 1 }, { 116, 50, -1 } });
    }

    private RecipeTankData() {}

    public static Map<Integer, Map<String, Object>> read(TemplateRecipeHandler handler) {
        Map<Integer, Map<String, Object>> result = new HashMap<>();
        int[][] positions = POSITIONS.get(
            handler.getClass()
                .getName());
        boolean alloy = handler.getClass()
            .getName()
            .equals("tconstruct.plugins.nei.RecipeHandlerAlloying");
        boolean casting = handler.getClass()
            .getName()
            .equals("tconstruct.plugins.nei.RecipeHandlerCastingTable")
            || handler.getClass()
                .getName()
                .equals("tconstruct.plugins.nei.RecipeHandlerCastingBasin");
        if ((positions == null && !alloy && !casting) || RecipeItemVariants.isCollecting()) return result;
        try {
            for (Map<String, Object> record : NEIFluidTankDumper.dump(handler)) {
                Map<String, Object> data = new LinkedHashMap<>();
                List<Object> inputs = new ArrayList<>(), outputs = new ArrayList<>();
                for (Object object : (List<?>) record.get("tanks")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tank = (Map<String, Object>) object;
                    int x = ((Number) tank.get("x")).intValue(), y = ((Number) tank.get("y")).intValue();
                    int index = 0;
                    if (positions != null)
                        for (int[] position : positions) if (position[0] == x && position[1] == y) index = position[2];
                    if (alloy && y == 9) index = x == 118 ? -1 : (x >= 21 && x < 57 ? inputs.size() + 1 : 0);
                    if (casting && x == 60 && y == 8) index = 1;
                    if (index == 0) throw new IllegalStateException("Unmapped recipe tank at " + x + "," + y);
                    List<Object> target = index > 0 ? inputs : outputs;
                    int slot = Math.abs(index) - 1;
                    while (target.size() <= slot) target.add(null);
                    List<Object> candidates = new ArrayList<>();
                    for (Object choice : (List<?>) tank.get("variants")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fluid = (Map<String, Object>) choice;
                        if (!fluid.containsKey("key")) continue;
                        Map<String, Object> item = new LinkedHashMap<>();
                        for (String key : new String[] { "key", "nbt", "tooltip", "showAmount", "perTick" }) {
                            if (fluid.get(key) != null) item.put(key, fluid.get(key));
                        }
                        if (!Boolean.FALSE.equals(fluid.get("showAmount"))) item.put("amount", fluid.get("amount"));
                        candidates.add(item);
                    }
                    if (!candidates.isEmpty())
                        target.set(slot, candidates.size() == 1 ? candidates.get(0) : candidates);
                }
                if (!inputs.isEmpty()) data.put("inputFluids", inputs);
                if (!outputs.isEmpty()) data.put("outputFluids", outputs);
                result.put(((Number) record.get("recipe")).intValue(), data);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                "Cannot export recipe tanks: " + handler.getClass()
                    .getName(),
                e);
        }
        return result;
    }
}
