package gregtech.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import gregtech.api.objects.overclockdescriber.EUNoOverclockDescriber;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/** GT说明采集：构造器是包可见，因此此桥接类位于gregtech.nei；只接收文字，不调用字体或OpenGL。 */
public final class DumperRecipeDisplayInfo extends RecipeDisplayInfo {

    private final List<String> lines = new ArrayList<>();

    private DumperRecipeDisplayInfo(GTRecipe recipe, RecipeMap<?> map, OverclockCalculator calculator) {
        super(recipe, map, new EUNoOverclockDescriber((byte) 1, map.getAmperage()), calculator, 0, -1);
    }

    @Override
    public void drawText(String text, int xStart, int yShift) {
        if (text == null || text.trim()
            .isEmpty()) return;
        lines.add(EnumChatFormatting.getTextWithoutFormattingCodes(text));
    }

    /** 使用NEI默认的无超频口径，包含frontend、特殊说明、metadata和配方来源。 */
    public static List<String> describe(GTRecipe recipe, RecipeMap<?> map) {
        OverclockCalculator calculator = OverclockCalculator.ofNoOverclock(recipe);
        calculator.calculate();
        DumperRecipeDisplayInfo info = new DumperRecipeDisplayInfo(recipe, map, calculator);
        map.getFrontend()
            .drawDescription(info);
        return info.lines;
    }
}
