package com.iouter.gtnhdumper.common.dumper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.utils.FBOHelper;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;

/** NEICustomDiagram: preserve every diagram's nodes; capture only individual sample components. */
public final class NEIDiagramDumper {

    private NEIDiagramDumper() {}

    public static Map<String, Object> dump(IRecipeHandler handler, File directory) throws Exception {
        if (!handler.getClass()
            .getName()
            .startsWith("com.github.dcysteine.neicustomdiagram.")) return null;
        Object state = field(handler, "diagramState");
        List<Map<String, Object>> records = new ArrayList<>();
        File assets = new File(directory, "diagram-assets");
        Files.createDirectories(assets.toPath());
        // Layout clones reuse their draw function; measure each renderer once, not every recipe slot.
        Map<Object, int[]> slotSizes = new IdentityHashMap<>();
        int index = 0;
        String prefix = "NEI-diagram-" + handler.getHandlerId()
            .replaceAll("[^a-zA-Z0-9_-]", "-");
        try (FBOHelper fbo = new FBOHelper(128)) {
            for (Object diagram : (Iterable<?>) field(handler, "diagrams")) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("index", index);
                Object dimension = call(diagram, "dimension", state);
                record.put("width", call(dimension, "width"));
                record.put("height", call(dimension, "height"));
                List<Map<String, Object>> nodes = new ArrayList<>();
                List<String> unsupported = new ArrayList<>();
                Object layout = field(diagram, "layout");
                for (Object lines : (Iterable<?>) call(layout, "lines")) {
                    int color = ((Number) call(call(lines, "colour"), "getColor")).intValue();
                    for (String kind : new String[] { "segments", "arrows" }) {
                        for (Object segment : (Iterable<?>) call(lines, kind)) {
                            Map<String, Object> node = point(call(segment, "a"));
                            Map<String, Object> end = point(call(segment, "b"));
                            node.put("kind", kind.equals("arrows") ? "arrow" : "line");
                            node.put("endX", end.get("x"));
                            node.put("endY", end.get("y"));
                            node.put("color", color);
                            nodes.add(node);
                        }
                    }
                }
                for (Object slot : (Iterable<?>) call(layout, "allSlots")) {
                    Map<String, Object> node = point(call(slot, "position"));
                    node.put("kind", "slot");
                    node.put("width", call(slot, "slotWidth"));
                    Object renderer = call(slot, "drawFunction");
                    int[] dimensions = slotSizes.get(renderer);
                    if (dimensions == null) {
                        BufferedImage rendered = render(slot, state, node, 32, fbo);
                        int left = rendered.getWidth(), top = rendered.getHeight(), right = 0, bottom = 0;
                        for (int y = 0; y < rendered.getHeight(); y++) {
                            for (int x = 0; x < rendered.getWidth(); x++) {
                                if ((rendered.getRGB(x, y) >>> 24) == 0) continue;
                                left = Math.min(left, x);
                                top = Math.min(top, y);
                                right = Math.max(right, x + 1);
                                bottom = Math.max(bottom, y + 1);
                            }
                        }
                        if (right <= left || bottom <= top
                            || left == 0
                            || top == 0
                            || right == rendered.getWidth()
                            || bottom == rendered.getHeight()) {
                            throw new IllegalStateException("Diagram slot rendering is empty or clipped");
                        }
                        dimensions = new int[] { right - left, bottom - top };
                        slotSizes.put(renderer, dimensions);
                    }
                    // FBO capture uses two pixels per game pixel; slotWidth only describes layout spacing.
                    node.put("renderWidth", dimensions[0] / 2.0);
                    node.put("renderHeight", dimensions[1] / 2.0);
                    if (index == 0) {
                        int size = 32; // Draw::drawBigSlot may exceed the layout spacing stored in slotWidth.
                        node.put("asset", capture(slot, state, node, size, fbo, assets, prefix, nodes.size()));
                    }
                    nodes.add(node);
                }
                for (Object label : (Iterable<?>) call(layout, "labels")) {
                    Map<String, Object> node = point(call(label, "position"));
                    Object dimensionLabel = call(label, "dimension");
                    node.put("width", call(dimensionLabel, "width"));
                    node.put("height", call(dimensionLabel, "height"));
                    try {
                        node.put("kind", "text");
                        node.put("text", call(label, "text"));
                        node.put("color", call(label, "colour"));
                        node.put("small", call(label, "small"));
                        node.put("shadow", call(label, "shadow"));
                    } catch (NoSuchMethodException ignored) {
                        node.put("kind", "label");
                        Object component = call(label, "component");
                        node.put("name", call(component, "description"));
                        if (index == 0)
                            node.put("asset", capture(label, state, node, 16, fbo, assets, prefix, nodes.size()));
                    }
                    nodes.add(node);
                }
                for (Object interactive : (Iterable<?>) field(diagram, "interactables")) {
                    Map<String, Object> node = point(call(interactive, "position"));
                    try {
                        List<Map<String, Object>> variants = new ArrayList<>();
                        for (Object component : (Iterable<?>) field(interactive, "components")) {
                            Map<String, Object> variant = new LinkedHashMap<>();
                            variant.put("name", call(component, "description"));
                            variant.put("type", String.valueOf(call(component, "type")));
                            Object stack = call(component, "stack");
                            if (stack instanceof ItemStack item) {
                                variant.put("key", Utils.getItemKey(item));
                                variant.put("nbt", Utils.getItemNBT(item));
                            } else if (stack instanceof FluidStack fluid) {
                                variant.put(
                                    "key",
                                    "fluid." + fluid.getFluid()
                                        .getName());
                            }
                            variant.put("amount", ((Optional<?>) call(component, "stackSize")).orElse(null));
                            variant
                                .put("additionalInfo", ((Optional<?>) call(component, "additionalInfo")).orElse(null));
                            variants.add(variant);
                        }
                        node.put("kind", "component");
                        node.put("variants", variants);
                        // Item/fluid variants are data; the wiki renders their existing icons.
                    } catch (NoSuchFieldException ignored) {
                        node.put("kind", "custom");
                        Object d = call(interactive, "dimension");
                        node.put("width", call(d, "width"));
                        node.put("height", call(d, "height"));
                        if (index == 0) {
                            int size = Math.max(
                                32,
                                Math.max(
                                    ((Number) node.get("width")).intValue(),
                                    ((Number) node.get("height")).intValue()) + 2);
                            node.put(
                                "asset",
                                capture(interactive, state, node, size, fbo, assets, prefix, nodes.size()));
                        }
                        unsupported.add(
                            interactive.getClass()
                                .getName() + ": interactive action not exported");
                    }
                    nodes.add(node);
                }
                record.put("nodes", nodes);
                record.put("unsupported", unsupported);
                records.add(record);
                index++;
            }
        }
        String filename = prefix + ".json";
        Files.write(
            new File(directory, filename).toPath(),
            GTNHDumper.GSON.toJson(records)
                .getBytes(StandardCharsets.UTF_8));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file", filename);
        result.put("count", records.size());
        if (!records.isEmpty()) result.put("sample", records.get(0));
        return result;
    }

    private static String capture(Object drawable, Object state, Map<String, Object> node, int size, FBOHelper fbo,
        File directory, String prefix, int index) throws Exception {
        BufferedImage image = render(drawable, state, node, size, fbo);
        String filename = prefix + "-" + index + ".png";
        FBOHelper.saveToFile(new File(directory, filename), image);
        return "diagram-assets/" + filename;
    }

    private static BufferedImage render(Object drawable, Object state, Map<String, Object> node, int size,
        FBOHelper fbo) throws Exception {
        int x = ((Number) node.get("x")).intValue(), y = ((Number) node.get("y")).intValue();
        fbo.begin();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(0, 64, 0, 64, -1000, 1000);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            GLStateManager.disableLighting();
            GLStateManager.disableDepthTest();
            GL11.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glColor4f(1, 1, 1, 1);
            GLStateManager.glTranslated(size / 2.0 - x, size / 2.0 - y, 0);
            call(drawable, "draw", state);
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix();
            fbo.end();
        }
        BufferedImage image = fbo.saveToImage()
            .getSubimage(0, 0, size * 2, size * 2);
        fbo.restoreTexture();
        return image;
    }

    private static Map<String, Object> point(Object point) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", call(point, "x"));
        result.put("y", call(point, "y"));
        return result;
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(object);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object object, String name, Object... args) throws Exception {
        for (Method method : object.getClass()
            .getMethods()) {
            if (method.getName()
                .equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(object, args);
            }
        }
        throw new NoSuchMethodException(
            object.getClass()
                .getName() + "."
                + name);
    }
}
