package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.ProgressBar;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.iouter.gtnhdumper.common.utils.FBOHelper;

/** GT 界面按控件导出：窗口底板与槽框不绘制，独立纹理直接渲染，保留纹理标识和 UV。 */
public final class NEIComponentDumper {

    private NEIComponentDumper() {}

    public static Map<String, Object> dump(Object handler, File directory, String prefix, FBOHelper fbo)
        throws Exception {
        ModularWindow window = (ModularWindow) field(handler, "modularWindow");
        List<Map<String, Object>> components = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        int ordinal = 0;
        for (Widget widget : window.getChildren()) {
            ordinal++;
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("x", widget.getPos().x);
            component.put("y", widget.getPos().y);
            component.put("width", widget.getSize().width);
            component.put("height", widget.getSize().height);
            component.put(
                "widgetClass",
                widget.getClass()
                    .getName());
            List<Map<String, Object>> layers = new ArrayList<>();
            component.put("layers", layers);
            String stem = prefix + "-widget-" + ordinal;
            if (widget instanceof SlotWidget) {
                component.put("kind", "slot");
                // 库存绑定与编号已在 slots 中记录。第一层是槽框，只记录，不导出图像。
                IDrawable[] backgrounds = widget.getBackground();
                if (backgrounds != null) {
                    for (int i = 0; i < backgrounds.length; i++) {
                        if (backgrounds[i] == null) continue;
                        Map<String, Object> layer = describe(backgrounds[i]);
                        layer.put("role", i == 0 ? "frame" : "overlay");
                        if (i > 0) save(backgrounds[i], widget, directory, stem + "-overlay-" + i, fbo, layer);
                        layers.add(layer);
                    }
                }
            } else if (widget instanceof ProgressBar) {
                component.put("kind", "progress");
                component.put("direction", String.valueOf(field(widget, "direction")));
                IDrawable empty = (IDrawable) field(widget, "emptyTexture");
                UITexture[] full = (UITexture[]) field(widget, "fullTexture");
                if (empty != null) {
                    Map<String, Object> layer = describe(empty);
                    layer.put("role", "empty");
                    save(empty, widget, directory, stem + "_frame", fbo, layer);
                    layers.add(layer);
                }
                if (full[0] != null) {
                    Map<String, Object> layer = describe(full[0]);
                    layer.put("role", "full");
                    save(full[0], widget, directory, stem + "_content", fbo, layer);
                    layers.add(layer);
                }
                if (String.valueOf(field(widget, "direction"))
                    .equals("CIRCULAR_CW")) {
                    unsupported.add(stem + ": circular progress requires a dedicated renderer");
                }
            } else if (widget instanceof DrawableWidget drawableWidget) {
                component.put("kind", "decoration");
                IDrawable drawable = drawableWidget.getDrawable();
                if (drawable != null) {
                    Map<String, Object> layer = describe(drawable);
                    save(drawable, widget, directory, stem, fbo, layer);
                    layers.add(layer);
                }
            } else {
                component.put("kind", "unsupported");
                unsupported.add(
                    stem + ": "
                        + widget.getClass()
                            .getName());
            }
            components.add(component);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "widget-selected-drawables");
        result.put("components", components);
        result.put("unsupported", unsupported);
        return result;
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                // 字段可能由父类声明，例如 ProgressBar 的定制子类。
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Map<String, Object> describe(IDrawable drawable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
            "drawableClass",
            drawable.getClass()
                .getName());
        if (drawable instanceof UITexture texture) {
            result.put("texture", texture.location.toString());
            result.put("u0", texture.u0);
            result.put("v0", texture.v0);
            result.put("u1", texture.u1);
            result.put("v1", texture.v1);
        }
        return result;
    }

    /** 直接调用独立 drawable 绘制到透明 FBO；尺寸来自控件，没有像素识别或抠图。 */
    private static void save(IDrawable drawable, Widget widget, File directory, String name, FBOHelper fbo,
        Map<String, Object> layer) throws Exception {
        int width = widget.getSize().width;
        int height = widget.getSize().height;
        if (width <= 0 || height <= 0 || width > 512 || height > 512) {
            throw new IllegalArgumentException("Invalid component dimensions: " + width + "x" + height);
        }
        fbo.begin();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(0, 512, 0, 512, -1000, 1000);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            GLStateManager.disableLighting();
            GLStateManager.disableDepthTest();
            GL11.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glColor4f(1, 1, 1, 1);
            drawable.draw(0, 0, width, height, 0);
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix();
            fbo.end();
        }
        try {
            FBOHelper.saveToFile(
                new File(directory, name + ".png"),
                fbo.saveToImage()
                    .getSubimage(0, 0, width * 2, height * 2));
            layer.put("file", name + ".png");
        } finally {
            fbo.restoreTexture();
        }
    }
}
