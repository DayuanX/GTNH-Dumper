package com.iouter.gtnhdumper.common.dumper;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Export rectangular NEI tanks and fluid identities; reuse existing item icons downstream. */
public final class NEIFluidTankDumper {

    private NEIFluidTankDumper() {}

    public static List<Map<String, Object>> dump(IRecipeHandler handler) throws Exception {
        List<Map<String, Object>> recipes = new ArrayList<>();
        if (!(handler instanceof TemplateRecipeHandler template)) return recipes;
        String name = handler.getClass()
            .getName();
        if (!(name.startsWith("buildcraft.compat.nei.") || name.startsWith("binnie.")
            || name.startsWith("tconstruct.plugins.nei.")
            || name.startsWith("forestry.")
            || name.startsWith("tonius.neiintegration.")
            || name.startsWith("net.bdew.gendustry.nei.")
            || name.equals("crazypants.enderio.nei.VatRecipeHandler")
            || name.equals("ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler"))) return recipes;
        for (int index = 0; index < template.arecipes.size(); index++) {
            Object recipe = template.arecipes.get(index);
            List<Map<String, Object>> custom = customTanks(name, recipe);
            if (!custom.isEmpty()) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("recipe", index);
                record.put("tanks", custom);
                recipes.add(record);
                continue;
            }
            Method getter;
            try {
                getter = recipe.getClass()
                    .getMethod("getFluidTanks");
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            getter.setAccessible(true);
            Object value = getter.invoke(recipe);
            if (!(value instanceof Iterable<?>tanks)) continue;
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Object tank : tanks) {
                Map<String, Object> entry = new LinkedHashMap<>();
                Rectangle bounds = (Rectangle) field(tank, "position");
                entry.put("x", bounds.x);
                entry.put("y", bounds.y);
                entry.put("width", bounds.width);
                entry.put("height", bounds.height);
                Object storage = field(tank, "tank");
                int capacity = storage instanceof FluidTank f ? f.getCapacity() : (Integer) field(tank, "capacity");
                entry.put("capacity", capacity);
                entry.put("flowingTexture", Boolean.TRUE.equals(field(tank, "flowingTexture")));
                entry.put("overlayTexture", field(tank, "overlayTexture"));
                Object overlay = field(tank, "overlayTexturePos");
                if (overlay instanceof java.awt.Point point) {
                    entry.put("overlayX", point.x);
                    entry.put("overlayY", point.y);
                }
                List<Map<String, Object>> variants = new ArrayList<>();
                Object choices = field(tank, "tanks");
                if (choices instanceof FluidTank[]alternatives) {
                    for (FluidTank alternative : alternatives) {
                        variants.add(fluid(alternative.getFluid(), tank));
                    }
                } else {
                    FluidStack stack = storage instanceof FluidTank f ? f.getFluid()
                        : (FluidStack) field(tank, "fluid");
                    variants.add(fluid(stack, tank));
                }
                entry.put("variants", variants);
                entries.add(entry);
            }
            if (!entries.isEmpty()) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("recipe", index);
                record.put("tanks", entries);
                recipes.add(record);
            }
        }
        return recipes;
    }

    private static Object field(Object object, String name) throws Exception {
        if (object == null) return null;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                // Scala组件和各模组配方把字段声明在不同父类。
            }
        }
        return null;
    }

    private static Object call(Object object, String name) throws Exception {
        Method method = object.getClass()
            .getMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }

    /** 独立流体组件的源矩形；坐标转换与各处理器drawExtras一致。 */
    private static List<Map<String, Object>> customTanks(String name, Object recipe) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        if (name.startsWith("net.bdew.gendustry.nei.")) {
            Object iterator = call(call(recipe, "components"), "iterator");
            while (Boolean.TRUE.equals(call(iterator, "hasNext"))) {
                Object component = call(iterator, "next");
                if (!component.getClass()
                    .getSimpleName()
                    .equals("FluidComponent")) continue;
                Object rect = field(component, "rect");
                if (rect == null) rect = call(component, "rect");
                Rectangle bounds = new Rectangle(
                    ((Number) call(rect, "x")).intValue() - 5,
                    ((Number) call(rect, "y")).intValue() - 13,
                    ((Number) call(rect, "w")).intValue(),
                    ((Number) call(rect, "h")).intValue());
                result.add(
                    customTank(
                        (FluidStack) field(component, "fStack"),
                        ((Number) field(component, "capacity")).intValue(),
                        bounds,
                        component));
            }
        } else if (name.equals("crazypants.enderio.nei.VatRecipeHandler")) {
            FluidStack input = (FluidStack) field(recipe, "inFluid");
            FluidStack output = (FluidStack) field(recipe, "result");
            if (input != null) {
                input = input.copy();
                input.amount = ((Number) call(recipe, "getInputFluidAmount")).intValue();
                result.add(customTank(input, 8000, new Rectangle(25, 1, 15, 47), recipe));
            }
            if (output != null) {
                output = output.copy();
                output.amount = ((Number) call(recipe, "getResultFluidAmount")).intValue();
                result.add(customTank(output, 8000, new Rectangle(127, 1, 15, 47), recipe));
            }
        } else if (name.equals("ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler")) {
            result
                .add(customTank((FluidStack) field(recipe, "fluidInput"), 8000, new Rectangle(38, 50, 12, 47), recipe));
            result.add(
                customTank((FluidStack) field(recipe, "fluidOutput"), 8000, new Rectangle(116, 50, 12, 47), recipe));
        }
        return result;
    }

    private static Map<String, Object> customTank(FluidStack fluid, int capacity, Rectangle bounds, Object component)
        throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", bounds.x);
        result.put("y", bounds.y);
        result.put("width", bounds.width);
        result.put("height", bounds.height);
        result.put("capacity", capacity);
        result.put("variants", java.util.Collections.singletonList(fluid(fluid, component)));
        result.put(
            "source",
            component.getClass()
                .getName());
        return result;
    }

    private static Map<String, Object> fluid(FluidStack stack, Object tank) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        if (stack == null) return result;
        result.put(
            "key",
            "fluid." + stack.getFluid()
                .getName());
        result.put("name", stack.getLocalizedName());
        result.put("amount", stack.amount);
        result.put("nbt", stack.tag == null ? null : stack.tag.toString());
        result.put("showAmount", !Boolean.FALSE.equals(field(tank, "showAmount")));
        result.put("perTick", Boolean.TRUE.equals(field(tank, "perTick")));
        List<String> tooltip = new ArrayList<>();
        try {
            Method method = tank.getClass()
                .getMethod("handleTooltip", List.class);
            method.setAccessible(true);
            Object current = field(tank, "tank");
            if (current instanceof FluidTank storage) {
                // 候选流体的提示必须对应当前候选；恢复对象状态供后续NEI捕获使用。
                Field active = tank.getClass()
                    .getField("tank");
                active.setAccessible(true);
                try {
                    active.set(tank, new FluidTank(stack, storage.getCapacity()));
                    method.invoke(tank, tooltip);
                } finally {
                    active.set(tank, storage);
                }
            } else {
                method.invoke(tank, tooltip);
            }
            result.put("tooltip", tooltip);
        } catch (NoSuchMethodException ignored) {
            // Optional per-mod tooltip.
        }
        IIcon icon = Boolean.TRUE.equals(field(tank, "flowingTexture")) ? stack.getFluid()
            .getFlowingIcon() : null;
        if (icon == null) icon = stack.getFluid()
            .getStillIcon();
        int color = stack.getFluid()
            .getColor(stack);
        result.put("color", color);
        result.put("icon", icon == null ? null : icon.getIconName());
        // 图片由预处理按fluid key复用已有FluidDisplay图标；这里不生成流体纹理。
        return result;
    }
}
