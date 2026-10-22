package com.iouter.gtnhdumper.common.dumper;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.automation.ExportSession;
import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.utils.AllItemStacks;
import com.iouter.gtnhdumper.common.utils.DynamicTexture;
import com.iouter.gtnhdumper.common.utils.FBOHelper;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.PngWriter;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.Utils;

import bartworks.system.material.BWMetaGeneratedOres;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.shadow.org.apache.commons.csv.CSVFormat;
import codechicken.nei.shadow.org.apache.commons.csv.CSVPrinter;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.registry.GameData;
import gregtech.common.blocks.GTBlockOre;
import gtPlusPlus.core.block.base.BlockBaseOre;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class ItemIconDumper extends WikiDumper {

    private static final int SINGLE_FRAME_SIZE = 256;
    private static final Object2IntOpenHashMap<ItemStack> frameCountMap = new Object2IntOpenHashMap<>();
    private static final Object2IntOpenHashMap<ItemStack> dynamicSizeMap = new Object2IntOpenHashMap<>();
    private static final Map<Integer, FBOHelper> fbos = new HashMap<>();
    private static JsonObject resume;
    private static int resumedIcons;
    private static final Map<String, Integer> unregisteredItems = new LinkedHashMap<>();

    public ItemIconDumper() {
        super("tools.dump.gtnhdumper.itemicon");
    }

    @Override
    protected boolean supportsModFilter() {
        return true;
    }

    @Override
    public int getKeyIndex() {
        return 0;
    }

    @Override
    public String getKeyStr() {
        return "shortKey";
    }

    @Override
    public Iterable<Object[]> dumpObject(int mode) {
        long started = System.nanoTime();
        int workers = Integer.getInteger("gtnhdumper.pngWorkers", 0);
        PngWriter writer = new PngWriter(workers);
        resumedIcons = 0;
        unregisteredItems.clear();
        resume = null;
        File resumeFile = ExportSession.imageResume();
        if (resumeFile != null) {
            try {
                resume = GTNHDumper.GSON.fromJson(
                    new String(Files.readAllBytes(resumeFile.toPath()), java.nio.charset.StandardCharsets.UTF_8),
                    JsonObject.class);
            } catch (IOException error) {
                throw new IllegalStateException("Could not load resume image manifest", error);
            }
        }
        FBOHelper.setPngWriter(writer);
        try (writer) {
            return dumpIcons(mode);
        } finally {
            FBOHelper.setPngWriter(null);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("workers", workers);
            metrics.put("maximumCompression", true);
            metrics.put("writtenImages", writer.count());
            metrics.put("resumedIcons", resumedIcons);
            metrics.put("excludedUnregisteredItems", unregisteredItems);
            metrics.put("wallNanos", System.nanoTime() - started);
            metrics.put("encodingAndWriteTaskNanos", writer.encodeNanos());
            try {
                Files.write(
                    ModFilter.current()
                        .outputFile("icon-performance.json")
                        .toPath(),
                    GTNHDumper.GSON.toJson(metrics)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new IllegalStateException("Could not record icon export metrics", error);
            }
        }
    }

    private Iterable<Object[]> dumpIcons(int mode) {
        LinkedList<Object[]> list = new LinkedList<>();
        frameCountMap.clear();
        dynamicSizeMap.clear();

        List<ItemStack> itemStacks = AllItemStacks.getFilteredItemStacks();

        Map<String, String> redirectMap = new LinkedHashMap<>();
        Set<String> itemNameSet = new HashSet<>();
        Set<String> baseIconKeys = new HashSet<>();
        Map<String, ItemStack> rendered = new HashMap<>();
        Map<String, JsonObject> reference = loadReferenceMetadata();
        Map<String, String> missingReference = new LinkedHashMap<>();
        int visited = 0;

        for (ItemStack stack : itemStacks) {
            if (++visited % 1000 == 0) ExportSession.progress(
                "itemicon: " + visited + "/" + itemStacks.size() + " stacks; " + rendered.size() + " distinct icons");
            if (Utils.isStackInvalid(stack)) continue;
            // AdvItemPanelDumper also excludes unregistered display-only objects.
            // They have no stable registry key and would otherwise overwrite icon_null.png.
            if (GameData.getItemRegistry()
                .getNameForObject(stack.getItem()) == null) {
                String identity = stack.getItem()
                    .getClass()
                    .getName() + ":"
                    + stack.getItemDamage();
                unregisteredItems.merge(identity, 1, Integer::sum);
                continue;
            }
            if (usesBaseIcon(stack)) {
                stack = getBaseIconStack(stack);
                if (!baseIconKeys.add(Utils.getItemStackShortKey(stack))) continue;
            }
            String iconName = getIconFileName(stack);
            ItemStack previous = rendered.putIfAbsent(iconName, stack);
            if (previous == null) {
                if (reference == null) {
                    ItemStack renderStack = stack.copy();
                    // NBT copies may serialize map entries in a different order. Keep the
                    // original identity's filename even when the renderer works on a copy.
                    renderGeneralItem(
                        renderStack,
                        getFbo(SINGLE_FRAME_SIZE),
                        RenderItem.getInstance(),
                        iconName,
                        getIconFileName(stack, true));
                    if (frameCountMap.containsKey(renderStack))
                        frameCountMap.put(stack, frameCountMap.getInt(renderStack));
                    if (dynamicSizeMap.containsKey(renderStack))
                        dynamicSizeMap.put(stack, dynamicSizeMap.getInt(renderStack));
                } else {
                    JsonObject metadata = reference.get(Utils.getItemStackShortKey(stack));
                    if (metadata == null || !iconName.equals(
                        metadata.get("iconName")
                            .getAsString())) {
                        missingReference.put(Utils.getItemStackShortKey(stack), iconName);
                        continue;
                    }
                    if (metadata.has("frameCount") && !metadata.get("frameCount")
                        .isJsonNull()) frameCountMap.put(
                            stack,
                            metadata.get("frameCount")
                                .getAsInt());
                    if (metadata.has("size") && !metadata.get("size")
                        .isJsonNull()) dynamicSizeMap.put(
                            stack,
                            metadata.get("size")
                                .getAsInt());
                }
            } else {
                if (!new RecipeItemVariants.Key(previous).equals(new RecipeItemVariants.Key(stack)))
                    throw new IllegalStateException("Different item identities share icon filename: " + iconName);
                if (frameCountMap.containsKey(previous)) frameCountMap.put(stack, frameCountMap.getInt(previous));
                if (dynamicSizeMap.containsKey(previous)) dynamicSizeMap.put(stack, dynamicSizeMap.getInt(previous));
            }
            final String translatedName = EnumChatFormatting
                .getTextWithoutFormattingCodes(GuiContainerManager.itemDisplayNameShort(stack.copy()));
            final String imageName = getIconFileName(stack);
            final String vaildFileName = Utils.replaceIllegalChars(translatedName);
            if (itemNameSet.add(vaildFileName)) {
                redirectMap.put("File:" + vaildFileName + ".png", "File:" + imageName);
            } else {
                int i = 2;
                while (!itemNameSet.add(vaildFileName + " " + i)) {
                    i++;
                }
                redirectMap.put("File:" + vaildFileName + " " + i + ".png", "File:" + imageName);
            }
            list.add(
                new Object[] { Utils.getItemStackShortKey(stack), imageName, getItemDynamicSize(stack),
                    getItemFrameCount(stack) });
        }

        if (reference != null) {
            try {
                Files.write(
                    ModFilter.current()
                        .outputFile("image-mapping-diff.json")
                        .toPath(),
                    GTNHDumper.GSON.toJson(missingReference)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new IllegalStateException("Could not write image mapping comparison", error);
            }
            if (!missingReference.isEmpty()) throw new IllegalStateException(
                missingReference.size() + " new/changed icon mappings; see image-mapping-diff.json");
        }

        try (CSVPrinter printer = new CSVPrinter(
            Files.newBufferedWriter(
                ModFilter.current()
                    .outputFile("image_redirect.csv")
                    .toPath()),
            CSVFormat.DEFAULT)) {
            for (Map.Entry<String, String> e : redirectMap.entrySet()) {
                printer.printRecord(e.getKey(), e.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return list;
    }

    private static Map<String, JsonObject> loadReferenceMetadata() {
        File file = ExportSession.imageMetadata();
        if (file == null) return null;
        try {
            JsonObject data = GTNHDumper.GSON.fromJson(
                new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8),
                JsonObject.class);
            Map<String, JsonObject> reference = new HashMap<>();
            for (JsonElement element : data.getAsJsonArray("shortKey")) {
                JsonObject row = element.getAsJsonObject();
                String key = row.get("shortKey")
                    .getAsString();
                JsonObject previous = reference.putIfAbsent(key, row);
                if (previous != null && !previous.equals(row))
                    throw new IllegalStateException("Conflicting reference image metadata: " + key);
            }
            return reference;
        } catch (IOException error) {
            throw new IllegalStateException("Could not load reference image metadata", error);
        }
    }

    @Override
    public String[] header() {
        return new String[] { "shortKey", "iconName", "size", "frameCount" };
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation("nei.options.tools.dump.gtnhdumper.itemicon.dumped", file.getPath());
    }

    public static String getIconFileName(ItemStack stack) {
        return getIconFileName(stack, false);
    }

    public static String getIconFileName(ItemStack stack, boolean isDynamic) {
        return "icon_" + Utils.replaceHuijiIllegalChars(Utils.getItemStackShortKey(getBaseIconStack(stack)))
            + (isDynamic ? "_dynamic" : "")
            + ".png";
    }

    public static void testHashNBT() {
        Map<String, String> hashMap = new HashMap<>();
        int collisionCount = 0;
        List<ItemStack> itemStacks = AllItemStacks.getAllItemStacks();

        for (ItemStack stack : itemStacks) {
            String nbt = Utils.getItemNBT(stack);
            String hashNBT = Utils.hashNBT(stack);
            if (hashNBT == null) continue;
            String temp = hashMap.get(nbt);
            if (temp == null) {
                hashMap.put(nbt, hashNBT);
            } else if (!hashNBT.equals(temp)) {
                collisionCount++;
                GTNHDumper.error("冲突发生在" + nbt + "，对应编码为：" + hashNBT);
            }
        }

        GTNHDumper.debug("冲突数量: " + collisionCount);
        GTNHDumper.debug("唯一率: " + (100 - (collisionCount * 100.0 / Math.max(1, itemStacks.size()))) + "%");
    }

    private static boolean usesBaseIcon(ItemStack stack) {
        String registryName = GameData.getItemRegistry()
            .getNameForObject(stack.getItem());
        return "gregtech:gt.Volumetric_Flask".equals(registryName)
            || "gregtech:gt.Volumetric_Flask_8k".equals(registryName)
            || "gregtech:gt.Volumetric_Flask_32k".equals(registryName)
            || "gregtech:gt.Volumetric_Flask_Infinite".equals(registryName)
            || "miscutils:gt.Volumetric_Flask_8k".equals(registryName)
            || "miscutils:gt.Volumetric_Flask_32k".equals(registryName)
            || "miscutils:gt.Volumetric_Flask_Infinite".equals(registryName)
            || "appliedenergistics2:item.ItemFacade".equals(registryName);
    }

    private static ItemStack getBaseIconStack(ItemStack stack) {
        if (!usesBaseIcon(stack) || !stack.hasTagCompound()) return stack;
        // Keep the shared item stacks intact for item, recipe and ore dictionary exports.
        ItemStack baseStack = stack.copy();
        baseStack.setTagCompound(null);
        return baseStack;
    }

    public static void prepareRenderItem(ItemStack itemStack, RenderItem itemRenderer) {
        renderGeneralItem(itemStack, getFbo(SINGLE_FRAME_SIZE), itemRenderer);
    }

    private static FBOHelper getFbo(int size) {
        FBOHelper fbo = fbos.get(size);
        if (fbo == null) {
            fbo = new FBOHelper(size);
            fbos.put(size, fbo);
        }
        return fbo;
    }

    /**
     * Created by Jerrell Fang on 2/23/2015.
     *
     * @author Meow J
     *         <p>
     *         Borrowed from <a href=
     *         "https://github.com/Snownee/Item-Render-Dark/blob/master/src/main/java/itemrender/rendering/Renderer.java">Item-Render-Rebirth</a>
     */
    public static BufferedImage renderItem(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer, float scale,
        DynamicTexture dynamicTexture) {
        Minecraft minecraft = FMLClientHandler.instance()
            .getClient();
        fbo.begin();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(0, 16, 0, 16, -150.0F, 150.0F);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        RenderHelper.enableGUIStandardItemLighting();
        GLStateManager.enableRescaleNormal();
        GLStateManager.enableColorMaterial();
        GLStateManager.enableLighting();
        GLStateManager.glTranslated(8 * (1 - scale), 8 * (1 - scale), 0);
        GLStateManager.glScaled(scale, scale, scale);
        itemRenderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, itemStack, 0, 0);
        if (dynamicTexture != null) {
            dynamicTexture.updateAnimation();
        }
        GLStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        fbo.end();
        if (itemStack.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) itemStack.getItem()).field_150939_a;
            if (CommonProxy.isGTLoaded && (block instanceof GTBlockOre || block instanceof BWMetaGeneratedOres
                || block instanceof BlockBaseOre)) {
                return makeNonTransparentOpaque(fbo.saveToImage());
            }
        }
        return fbo.saveToImage();
    }

    public static void renderGeneralItem(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer) {
        itemStack = getBaseIconStack(itemStack);
        renderGeneralItem(itemStack, fbo, itemRenderer, getIconFileName(itemStack), getIconFileName(itemStack, true));
    }

    private static void renderGeneralItem(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer,
        String staticName, String dynamicName) {
        DynamicTexture dynamicTexture = new DynamicTexture(itemStack);
        boolean dynamic = !staticName.contains("Botania:prismarine") && dynamicTexture.isDynamic();
        if (resumeItem(itemStack, dynamic, staticName, dynamicName)) return;
        if (dynamic) {
            renderDynamicItem(itemStack, itemRenderer, dynamicTexture, staticName, dynamicName);
            return;
        }
        BufferedImage image = renderItem(itemStack, fbo, itemRenderer, 1f, null);
        FBOHelper.saveToFile(
            ModFilter.current()
                .outputFile("icons/" + staticName),
            image);
        fbo.restoreTexture();
    }

    private static boolean resumeItem(ItemStack stack, boolean dynamic, String name, String animation) {
        if (resume == null) return false;
        JsonObject images = resume.getAsJsonObject("images");
        if (!images.has(name) || (dynamic && !images.has(animation))) return false;
        com.google.gson.JsonArray dimensions = images.getAsJsonArray(name);
        if (dimensions.get(0)
            .getAsInt() != SINGLE_FRAME_SIZE
            || dimensions.get(1)
                .getAsInt() != SINGLE_FRAME_SIZE)
            return false;
        int size = 0;
        int frames = 0;
        if (dynamic) {
            dimensions = images.getAsJsonArray(animation);
            size = dimensions.get(1)
                .getAsInt();
            int width = dimensions.get(0)
                .getAsInt();
            if (size <= 0 || width < size || width % size != 0) return false;
            frames = width / size;
        }
        // Link only fully validated files; missing pairs are rendered into new files.
        linkResumedImage(name);
        if (dynamic) {
            linkResumedImage(animation);
            frameCountMap.put(stack, frames);
            dynamicSizeMap.put(stack, size);
        }
        resumedIcons++;
        return true;
    }

    private static void linkResumedImage(String name) {
        java.nio.file.Path source = new File(
            resume.get("directory")
                .getAsString(),
            name).toPath();
        java.nio.file.Path target = ModFilter.current()
            .outputFile("icons/" + name)
            .toPath();
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.createLink(target, source);
            } catch (IOException unsupportedLink) {
                Files.copy(source, target);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not reuse PNG: " + name, error);
        }
    }

    public static void renderDynamicItem(ItemStack itemStack, RenderItem itemRenderer, DynamicTexture dynamicTexture) {
        renderDynamicItem(
            itemStack,
            itemRenderer,
            dynamicTexture,
            getIconFileName(itemStack),
            getIconFileName(itemStack, true));
    }

    private static void renderDynamicItem(ItemStack itemStack, RenderItem itemRenderer, DynamicTexture dynamicTexture,
        String staticName, String dynamicName) {
        int frameCount = dynamicTexture.lcm;
        boolean is3dBlockItem = is3dBlockItem(itemStack);
        FBOHelper dynamicFbo = getFbo(is3dBlockItem ? SINGLE_FRAME_SIZE : getDynamicTextureSize(dynamicTexture));
        BufferedImage[] images = new BufferedImage[frameCount];
        for (int i = 0; i < frameCount; i++) {
            images[dynamicTexture.getIndex()] = renderItem(itemStack, dynamicFbo, itemRenderer, 1f, dynamicTexture);
        }
        BufferedImage image;
        if (Arrays.stream(images)
            .anyMatch(Objects::isNull)) {
            GTNHDumper.LOG.warn("{}'s frames has null frame", itemStack.getDisplayName());
            images = Arrays.stream(images)
                .filter(Objects::nonNull)
                .toArray(BufferedImage[]::new);
        }
        image = concatenateImages(images);
        frameCountMap.put(itemStack, image.getWidth() / image.getHeight());
        dynamicSizeMap.put(itemStack, image.getHeight());
        FBOHelper.saveToFile(
            ModFilter.current()
                .outputFile("icons/" + staticName),
            resizeImage(images[0], SINGLE_FRAME_SIZE));
        FBOHelper.saveToFile(
            ModFilter.current()
                .outputFile("icons/" + dynamicName),
            image);
        dynamicFbo.restoreTexture();
    }

    private static boolean is3dBlockItem(ItemStack itemStack) {
        return itemStack.getItem() instanceof ItemBlock
            && RenderBlocks.renderItemIn3d(((ItemBlock) itemStack.getItem()).field_150939_a.getRenderType());
    }

    private static int getDynamicTextureSize(DynamicTexture dynamicTexture) {
        return dynamicTexture.textures.stream()
            .mapToInt(texture -> Math.max(texture.getIconWidth(), texture.getIconHeight()))
            .max()
            .orElse(SINGLE_FRAME_SIZE);
    }

    private static BufferedImage resizeImage(BufferedImage image, int size) {
        if (image.getWidth() == size && image.getHeight() == size) {
            return image;
        }
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setComposite(AlphaComposite.Src);
        g2d.drawImage(image, 0, 0, size, size, null);
        g2d.dispose();
        return resized;
    }

    /**
     * 横向拼接BufferedImage数组（自动处理高度不一致、透明度）
     *
     * @param images 要拼接的图像数组
     * @return 拼接后的大图
     */
    public static BufferedImage concatenateImages(BufferedImage[] images) {
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("Image array cannot be empty");
        }

        if (deduplicateByPixel(images)) {
            return images[0];
        }

        // 1. 计算目标图像尺寸
        int totalWidth = Arrays.stream(images)
            .mapToInt(BufferedImage::getWidth)
            .sum();
        int maxHeight = Arrays.stream(images)
            .mapToInt(BufferedImage::getHeight)
            .max()
            .orElse(0);

        // 2. 创建带透明通道的目标图像 (ARGB)
        BufferedImage result = new BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();

        // 关键渲染设置（消除锯齿，保持透明度）
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.Src); // 保持源图像透明度

        // 3. 逐个绘制图像
        int currentX = 0;
        for (BufferedImage img : images) {
            int y = (maxHeight - img.getHeight()) / 2; // 垂直居中对齐
            g2d.drawImage(img, currentX, y, null);
            currentX += img.getWidth();
        }

        g2d.dispose();
        return result;
    }

    public static BufferedImage makeNonTransparentOpaque(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha != 0) { // 不是完全透明
                    argb = (0xFF << 24) | (argb & 0x00FFFFFF);
                    img.setRGB(x, y, argb);
                }
            }
        }
        return img;
    }

    /**
     * 如果数组中所有图像像素完全相同，返回仅包含第一个元素的数组；否则返回原数组。
     *
     * @param images BufferedImage 数组
     * @return 是否有重复
     */
    public static boolean deduplicateByPixel(BufferedImage[] images) {
        if (images == null || images.length <= 1) {
            return true;
        }

        BufferedImage first = images[0];
        // 如果第一张为 null，则直接返回原数组（或根据需求处理）
        if (first == null) {
            return false;
        }

        int width = first.getWidth();
        int height = first.getHeight();

        // 逐张比较
        for (int i = 1; i < images.length; i++) {
            BufferedImage curr = images[i];
            if (curr == null) {
                return false; // 存在 null，视为不同
            }
            // 尺寸不一致直接判定不同
            if (curr.getWidth() != width || curr.getHeight() != height) {
                return false;
            }
            // 快速比较：获取像素数组整数表示
            int[] firstPixels = first.getRGB(0, 0, width, height, null, 0, width);
            int[] currPixels = curr.getRGB(0, 0, width, height, null, 0, width);
            if (!Arrays.equals(firstPixels, currPixels)) {
                return false;
            }
        }

        // 全部相同，返回只含第一张的数组
        return true;
    }

    public static Integer getItemDynamicSize(ItemStack stack) {
        return dynamicSizeMap.containsKey(stack) ? dynamicSizeMap.getInt(stack) : null;
    }

    public static Integer getItemFrameCount(ItemStack stack) {
        return frameCountMap.containsKey(stack) ? frameCountMap.getInt(stack) : null;
    }
}
