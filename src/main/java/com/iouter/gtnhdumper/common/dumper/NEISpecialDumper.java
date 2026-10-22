package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.iouter.gtnhdumper.common.utils.FBOHelper;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Export representative recipe metadata and isolated native 3D nodes, without GUI backgrounds. */
public final class NEISpecialDumper {

    private NEISpecialDumper() {}

    public static Map<String, Object> dump(IRecipeHandler handler, int index, File directory) throws Exception {
        if (!(handler instanceof TemplateRecipeHandler template)) return null;
        Object recipe = template.arecipes.get(index);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : new String[] { "normalOutputsCount", "rareOutputsCount", "additionalOutputsCount",
            "infernalOutputsCount", "eliteCount", "ultraCount", "infernoCount", "eliteChance", "ultraChance",
            "infernoChance", "localizedName", "mobname", "numProductRows", "numSpecRows", "time", "mana", "manaUsage",
            "textLines", "reqLines", "power", "cookTime", "instability" }) {
            Object value = field(recipe, name);
            if (value instanceof Number || value instanceof String
                || value instanceof Boolean
                || value instanceof java.util.List<?>) result.put(name, value);
        }
        String cls = handler.getClass()
            .getName();
        if (cls.equals("com.kuba6000.mobsinfo.nei.MobHandler")
            || cls.equals("com.kuba6000.mobsinfo.nei.VillagerTradesHandler")) {
            Object entity = field(recipe, cls.endsWith("MobHandler") ? "mob" : "displayMob");
            if (entity instanceof EntityLivingBase living) result.put(
                "modelAsset",
                capture(
                    living,
                    false,
                    directory,
                    handler.getClass()
                        .getSimpleName()));
        } else if (cls.endsWith(".ReactorNEIHandler")) {
            Object core = Class
                .forName(
                    "com.brandon3055.draconicevolution.common.tileentities.multiblocktiles.reactor.TileReactorCore")
                .getDeclaredConstructor()
                .newInstance();
            result.put(
                "modelAsset",
                capture(
                    core,
                    true,
                    directory,
                    handler.getClass()
                        .getSimpleName()));
        }
        return result.isEmpty() ? null : result;
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /** 固定相机截取单个模型；槽框、列表和文字不进入该FBO。 */
    private static String capture(Object model, boolean reactor, File directory, String name) throws Exception {
        File assets = new File(directory, "model-assets");
        Files.createDirectories(assets.toPath());
        try (FBOHelper fbo = new FBOHelper(256)) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            fbo.begin();
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glOrtho(0, 128, 0, 128, -1000, 1000);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            try {
                GL11.glDisable(GL11.GL_CULL_FACE);
                GLStateManager.glColor4f(1, 1, 1, 1);
                if (reactor) {
                    GLStateManager.glTranslated(64, 64, 100);
                    GLStateManager.glScaled(260, 260, 260);
                    Object dispatcher = null;
                    for (Field f : TileEntityRendererDispatcher.class.getFields()) {
                        if (Modifier.isStatic(f.getModifiers()) && f.getType() == TileEntityRendererDispatcher.class)
                            dispatcher = f.get(null);
                    }
                    for (Method m : TileEntityRendererDispatcher.class.getMethods()) {
                        if (m.getParameterCount() == 5 && m.getParameterTypes()[0] == TileEntity.class) {
                            m.invoke(dispatcher, model, -0.5, -0.5, -0.5, 0.0f);
                            break;
                        }
                    }
                } else {
                    EntityLivingBase entity = (EntityLivingBase) model;
                    int scale = (int) Math.min(55 / Math.max(0.1, entity.height), 70 / Math.max(0.1, entity.width));
                    for (Method m : GuiInventory.class.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 6
                            && m.getParameterTypes()[5] == EntityLivingBase.class) {
                            m.invoke(null, 64, 100, scale, 0.0f, 0.0f, entity);
                            break;
                        }
                    }
                }
            } finally {
                GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                GLStateManager.glPopMatrix();
                fbo.end();
                GL11.glPopAttrib();
            }
            String filename = "NEI-model-" + name + ".png";
            FBOHelper.saveToFile(new File(assets, filename), fbo.saveToImage());
            fbo.restoreTexture();

            return "model-assets/" + filename;
        }
    }
}
