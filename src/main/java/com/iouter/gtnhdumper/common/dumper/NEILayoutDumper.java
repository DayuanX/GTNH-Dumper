package com.iouter.gtnhdumper.common.dumper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.recipe.ShapedCraftingHandlerRecipe;
import com.iouter.gtnhdumper.common.utils.FBOHelper;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.nei.GTNEIDefaultHandler;

/** Local NEI background capture and occupied-slot geometry; no Wiki API calls. */
public final class NEILayoutDumper {

    private NEILayoutDumper() {}

    public static void dump(File directory) throws Exception {
        Files.createDirectories(directory.toPath());
        List<Map<String, Object>> entries = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        try (FBOHelper fbo = new FBOHelper(1024)) {
            for (IRecipeHandler registered : GuiUsageRecipe.usagehandlers) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entries.add(entry);
                entry.put(
                    "class",
                    registered.getClass()
                        .getName());
                try {
                    String handlerId = registered.getHandlerId();
                    String overlay = registered.getOverlayIdentifier();
                    String identity = handlerId + "|" + overlay;
                    entry.put("handlerId", handlerId);
                    entry.put("overlayId", overlay);
                    entry.put("name", registered.getRecipeName());
                    entry.put(
                        "inputLayout",
                        ShapedCraftingHandlerRecipe.supports(registered.getClass()) ? "crafting-grid-3x3"
                            : "handler-specific");
                    if (!identities.add(identity)) {
                        entry.put("status", "duplicate-registration");
                        continue;
                    }
                    IRecipeHandler handler = loadHandler(registered);
                    int count = handler.numRecipes();
                    entry.put("recipeCount", count);
                    if (count == 0 && !(handler instanceof GTNEIDefaultHandler)) {
                        for (String getterName : new String[] { "getRecipeList", "getRecipes" }) {
                            try {
                                Object recipes = handler.getClass()
                                    .getMethod(getterName)
                                    .invoke(handler);
                                int size = recipes instanceof Map<?, ?>map ? map.size()
                                    : recipes instanceof java.util.Collection<?>collection ? collection.size() : -1;
                                if (size >= 0) entry.put("recipeSourceCount", size);
                            } catch (ReflectiveOperationException ignored) {
                                // 没有公开注册表接口的处理器仍保留加载结果为空的诊断。
                            }
                        }
                        if (Integer.valueOf(0)
                            .equals(entry.get("recipeSourceCount"))) {
                            throw new IllegalArgumentException(
                                "Recipe registry is empty in this client (verified via source getter)");
                        }
                        throw new IllegalArgumentException(
                            "All-recipes and identifier loaders returned no recipes; needs a concrete item query or handler-specific loading");
                    }
                    try {
                        Map<String, Object> diagram = NEIDiagramDumper.dump(handler, directory);
                        if (diagram != null) entry.put("diagram", diagram);
                    } catch (Exception error) {
                        entry.put("diagramError", error.toString());
                    }
                    HandlerInfo info = GuiRecipeTab.getHandlerInfo(handlerId, overlay == null ? "null" : overlay);
                    int width = info == null ? 166 : info.getWidth();
                    int height = info == null ? 65 : info.getHeight();
                    entry.put("modId", info == null ? "Unknown" : info.getModId());
                    Set<String> signatures = new LinkedHashSet<>();
                    List<Map<String, Object>> variants = new ArrayList<>();
                    boolean dynamic = handler instanceof GTNEIDefaultHandler gt
                        && com.iouter.gtnhdumper.common.utils.NEIDynamicSlots.supports(gt);
                    Map<String, List<Map<String, Object>>> widgetSlots = handler instanceof GTNEIDefaultHandler gt
                        ? gtSlots(gt)
                        : null;
                    Map<String, List<Map<String, Object>>> best = null;
                    int bestIndex = 0;
                    int bestCount = -1;
                    for (int i = 0; i < count; i++) {
                        Map<String, List<Map<String, Object>>> slots = slots(handler, i);
                        if (dynamic) {
                            slots = new LinkedHashMap<>(widgetSlots);
                            slots.putAll(
                                com.iouter.gtnhdumper.common.utils.NEIDynamicSlots
                                    .read((GTNEIDefaultHandler) handler, i));
                        }
                        boolean newGeometry = signatures.add(GTNHDumper.GSON.toJson(slots));
                        if (dynamic && newGeometry) {
                            Map<String, Object> variant = new LinkedHashMap<>();
                            variant.put("sampleRecipe", i);
                            variant.put(
                                "specialValue",
                                ((GTNEIDefaultHandler.CachedDefaultRecipe) ((GTNEIDefaultHandler) handler).arecipes
                                    .get(i)).mRecipe.mSpecialValue);
                            variant.put("slots", slots);
                            variants.add(variant);
                        }
                        int size = slots.values()
                            .stream()
                            .mapToInt(List::size)
                            .sum();
                        if (size > bestCount) {
                            best = slots;
                            bestIndex = i;
                            bestCount = size;
                        }
                    }
                    if (handler instanceof GTNEIDefaultHandler gt) {
                        if (!dynamic || best == null) best = widgetSlots;
                        entry.put(
                            "slotSource",
                            dynamic ? "ModularUI widgets plus recipe-dependent overlay positions"
                                : "ModularUI widget inventory and slot index; includes empty slots");
                        if (dynamic) entry.put("layoutVariants", variants);
                    } else {
                        entry.put("slotSource", "largest occupied-slot recipe sample");
                    }
                    height = Math.max(height, handler.getRecipeHeight(bestIndex));
                    if (entry.get("diagram") instanceof Map<?, ?>diagram
                        && diagram.get("sample") instanceof Map<?, ?>sample) {
                        width = Math.max(width, ((Number) sample.get("width")).intValue() + 3);
                        height = Math.max(height, ((Number) sample.get("height")).intValue() + 3);
                    }
                    int minX = 0;
                    int minY = 0;
                    for (List<Map<String, Object>> group : best.values()) {
                        for (Map<String, Object> slot : group) {
                            int x = (int) slot.get("x");
                            int y = (int) slot.get("y");
                            minX = Math.min(minX, x - 1);
                            minY = Math.min(minY, y - 1);
                            width = Math.max(width, x + 17);
                            height = Math.max(height, y + 17);
                        }
                    }
                    width -= minX;
                    height -= minY;
                    int captureSize = 1024;
                    while (captureSize < Math.max(width, height) * 2) captureSize *= 2;
                    int maximumTexture = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
                    if (captureSize > maximumTexture) throw new IllegalArgumentException(
                        "Layout needs " + captureSize + " pixels; GPU texture limit is " + maximumTexture);
                    entry.put("width", width);
                    entry.put("height", height);
                    entry.put("originX", minX);
                    entry.put("originY", minY);
                    entry.put("slots", best);
                    entry.put("sampleRecipe", bestIndex);
                    entry.put("geometryVariants", signatures.size());
                    try {
                        entry.put("fluidTankRecipes", NEIFluidTankDumper.dump(handler));
                    } catch (Exception error) {
                        entry.put("fluidTankError", error.toString());
                    }
                    try {
                        entry.put("special", NEISpecialDumper.dump(handler, bestIndex, directory));
                    } catch (Exception error) {
                        entry.put("specialError", error.toString());
                    }
                    String file = String.format("NEI-%03d.png", entries.size());
                    if (captureSize != 1024) fbo.resize(captureSize);
                    BufferedImage background;
                    try {
                        background = capture(handler, bestIndex, fbo, width, height, minX, minY, captureSize / 2);
                    } finally {
                        if (captureSize != 1024) fbo.resize(1024);
                    }
                    int visible = 0;
                    for (int y = 0; y < background.getHeight(); y++) {
                        for (int x = 0; x < background.getWidth(); x++) {
                            if ((background.getRGB(x, y) >>> 24) != 0) visible++;
                        }
                    }
                    entry.put("backgroundVisible", visible > 0);
                    FBOHelper.saveToFile(new File(directory, file), background);
                    entry.put("background", file);
                    if (handler instanceof GTNEIDefaultHandler || handler.getClass()
                        .getName()
                        .equals("gtnhintergalactic.nei.SpacePumpModuleRecipeHandler")
                        || handler.getClass()
                            .getName()
                            .equals("gtnhintergalactic.nei.GasSiphonRecipeHandler")) {
                        try {
                            entry.put(
                                "selective",
                                NEIComponentDumper.dump(handler, directory, file.replace(".png", ""), fbo));
                        } catch (Exception error) {
                            entry.put(
                                "selectiveError",
                                error.getClass()
                                    .getSimpleName() + ": "
                                    + error.getMessage());
                        }
                    }
                    entry.put("status", "captured");
                    entry.put(
                        "limitations",
                        "Background and largest occupied-slot sample only; foreground text, animation and recipe-dependent layouts require review");
                } catch (Exception | LinkageError error) {
                    // 模组在 startDrawing 后抛错时结束未提交批次，隔离后续界面的渲染状态。
                    try {
                        Tessellator.instance.draw();
                    } catch (RuntimeException ignored) {
                        // 没有未提交的批次时 draw 抛出 Not tesselating。
                    }
                    entry.put("status", "failed");
                    entry.put(
                        "reason",
                        error.getClass()
                            .getSimpleName() + ": "
                            + error.getMessage());
                }
                // Persist partial coverage after every handler so one native crash does not erase the audit trail.
                Files.write(
                    new File(directory, "layouts.json").toPath(),
                    GTNHDumper.GSON.toJson(entries)
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** 按处理器的实际查询标识加载；配方与布局导出共用，避免工作台仅导出空列表。 */
    public static IRecipeHandler loadHandler(IRecipeHandler registered) throws Exception {
        if (!(registered instanceof TemplateRecipeHandler template)) {
            String className = registered.getClass()
                .getName();
            if (!className.startsWith("com.github.dcysteine.neicustomdiagram.")) {
                throw new IllegalArgumentException(
                    "Item-query/interactive view: registered instance is not initialized; requires a concrete query adapter");
            }
            // DiagramGroup 等实现标准 ICraftingHandler，按 groupId 或 all 加载整组。
            if (registered instanceof ICraftingHandler crafting) {
                Set<String> diagramIds = new LinkedHashSet<>();
                diagramIds.add(registered.getHandlerId());
                diagramIds.add(registered.getOverlayIdentifier());
                diagramIds.add("all");
                // 末影储存概览以专用 global/personal 标识加载；空存档也返回真实的无数据界面。
                if (className.endsWith(".CustomDiagramGroup")) {
                    Field behaviors = registered.getClass()
                        .getDeclaredField("customBehaviorMap");
                    behaviors.setAccessible(true);
                    for (Object key : ((Map<?, ?>) behaviors.get(registered)).keySet()) {
                        diagramIds.add((String) key);
                    }
                }
                for (String id : diagramIds) {
                    if (id == null || id.isEmpty()) continue;
                    IRecipeHandler loaded = crafting.getRecipeHandler(id);
                    if (loaded.numRecipes() > 0) return loaded;
                }
            }
            throw new IllegalArgumentException(
                "Identifier/all loaders returned no views; this handler needs an item query or interactive state");
        }
        if (template.getClass()
            .getName()
            .equals("galaxyspace.core.nei.CustomRockRecipeHandler")) {
            TemplateRecipeHandler candidate = com.iouter.gtnhdumper.common.utils.NEIHandlerInstances.create(template);
            candidate.loadCraftingRecipes(
                (String) template.getClass()
                    .getField("recipeId")
                    .get(template));
            return candidate;
        }
        if (template.getClass()
            .getName()
            .equals("com.science.gtnl.client.nei.NuclearReactorGuideHandler")) {
            // This guide has no crafting/all query; its usage query is keyed by the reactor controller.
            Object controller = Class.forName("com.science.gtnl.utils.enums.GTNLItemList")
                .getField("NuclearReactor")
                .get(null);
            TemplateRecipeHandler candidate = template.newInstance();
            candidate.loadUsageRecipes(((gregtech.api.interfaces.IItemContainer) controller).get(1));
            return candidate;
        }
        IRecipeHandler loaded = template.getAllRecipeHandler();
        if (loaded.numRecipes() > 0) return loaded;
        Set<String> ids = new LinkedHashSet<>();
        ids.add(template.getOverlayIdentifier());
        ids.add(template.getHandlerId());
        for (String name : new String[] { "getRecipeId", "getRecipeID", "getRecipeIdentifier" }) {
            for (Class<?> type = template.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Method getter = type.getDeclaredMethod(name);
                    if (getter.getReturnType() != String.class) continue;
                    getter.setAccessible(true);
                    ids.add((String) getter.invoke(template));
                    break;
                } catch (NoSuchMethodException ignored) {
                    // Try the superclass that owns this identifier accessor.
                }
            }
        }
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            TemplateRecipeHandler candidate = template.newInstance();
            candidate.loadCraftingRecipes(id);
            if (candidate.numRecipes() > 0) return candidate;
        }
        // 部分工作台处理器只实现物品查询。用同模组注册配方的真实输出触发加载。
        String name = template.getClass()
            .getName();
        String recipePrefix = name.startsWith("mrtjp.projectred.") ? "mrtjp.projectred."
            : name.startsWith("gcewing.projectblue.") ? "gcewing.projectblue."
                : name.startsWith("ic2.") && name.contains("Shapeless") ? "ic2."
                    : name.startsWith("com.bymarcin.openglasses.") ? "com.bymarcin.openglasses." : null;
        if (recipePrefix != null) {
            for (Object value : CraftingManager.getInstance()
                .getRecipeList()) {
                if (!(value instanceof IRecipe recipe) || !value.getClass()
                    .getName()
                    .startsWith(recipePrefix)) continue;
                ItemStack output = recipe.getRecipeOutput();
                if (output == null || output.getItem() == null) continue;
                TemplateRecipeHandler candidate = template.newInstance();
                candidate.loadCraftingRecipes(output);
                if (candidate.numRecipes() > 0) return candidate;
            }
        }
        return loaded;
    }

    /** GT 槽位从窗口库存绑定读取；编号保留空槽位置，避免配方样本缺项导致后续槽位错位。 */
    private static Map<String, List<Map<String, Object>>> gtSlots(GTNEIDefaultHandler handler) throws Exception {
        Field windowField = GTNEIDefaultHandler.class.getDeclaredField("modularWindow");
        Field contextField = GTNEIDefaultHandler.class.getDeclaredField("templateContext");
        windowField.setAccessible(true);
        contextField.setAccessible(true);
        ModularWindow window = (ModularWindow) windowField.get(handler);
        Object context = contextField.get(handler);
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Widget child : window.getChildren()) {
            if (!(child instanceof SlotWidget widget)) continue;
            for (String group : new String[] { "itemInputs", "itemOutputs", "fluidInputs", "fluidOutputs",
                "specialSlot" }) {
                Object inventory = context.getClass()
                    .getField(group + "Inventory")
                    .get(context);
                if (inventory != widget.getMcSlot()
                    .getItemHandler()) continue;
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("x", widget.getPos().x + 1);
                slot.put("y", widget.getPos().y + 1);
                slot.put(
                    "index",
                    widget.getMcSlot()
                        .getSlotIndex() + 1);
                groups.computeIfAbsent(group, key -> new ArrayList<>())
                    .add(slot);
            }
        }
        return groups;
    }

    private static Map<String, List<Map<String, Object>>> slots(IRecipeHandler handler, int recipe) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        add(groups, "Inputs", handler.getIngredientStacks(recipe));
        PositionedStack result = handler.getResultStack(recipe);
        add(groups, "Outputs", result == null ? Collections.emptyList() : Collections.singletonList(result));
        add(groups, handler instanceof GTNEIDefaultHandler ? "Outputs" : "Other", handler.getOtherStacks(recipe));
        return groups;
    }

    private static void add(Map<String, List<Map<String, Object>>> groups, String role, List<PositionedStack> stacks) {
        if (stacks == null) return;
        for (PositionedStack stack : stacks) {
            if (stack == null) continue;
            // Only virtual fluid-display items represent fluid slots. Filled containers remain item slots.
            boolean fluid = stack.item != null && stack.item.getItem()
                .getClass()
                .getSimpleName()
                .contains("FluidDisplay");
            String group = (fluid ? "fluid" : "item") + role;
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("x", stack.relx);
            slot.put("y", stack.rely);
            groups.computeIfAbsent(group, key -> new ArrayList<>())
                .add(slot);
        }
    }

    private static BufferedImage capture(IRecipeHandler handler, int recipe, FBOHelper fbo, int width, int height,
        int originX, int originY, int projectionSize) {
        fbo.begin();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        // FBO readback starts at the bottom row; this projection directly yields top-down image rows.
        GLStateManager.glOrtho(0, projectionSize, 0, projectionSize, -1000, 1000);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            GLStateManager.disableLighting();
            GLStateManager.disableDepthTest();
            GL11.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glColor4f(1, 1, 1, 1);
            GLStateManager.glTranslated(-originX, -originY, 0);
            if (handler.numRecipes() == 0 && handler instanceof GTNEIDefaultHandler) {
                try {
                    Field field = GTNEIDefaultHandler.class.getDeclaredField("modularWindow");
                    field.setAccessible(true);
                    Method draw = GTNEIDefaultHandler.class.getDeclaredMethod("drawUI", ModularWindow.class);
                    draw.setAccessible(true);
                    draw.invoke(handler, field.get(handler));
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Empty GT interface could not draw its static window", error);
                }
            } else {
                handler.drawBackground(recipe);
            }
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix();
            fbo.end();
        }
        BufferedImage result = fbo.saveToImage()
            .getSubimage(0, 0, width * 2, height * 2);
        fbo.restoreTexture();
        return result;
    }
}
