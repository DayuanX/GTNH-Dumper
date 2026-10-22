package com.iouter.gtnhdumper.common.dumper;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameData;

/** Item identity selects rendering requirements independently of display names and NBT. */
final class ItemIconRenderProfile {

    private static final ItemIconRenderProfile DEFAULT = new ItemIconRenderProfile(null, false);
    // Unlabelled tesseract edges repeat every six seconds. Wide-line triangles must survive front-face culling.
    private static final ItemIconRenderProfile TESSERACT = new ItemIconRenderProfile(new Sampling(120, 64, 50), true);

    final Sampling sampling;
    final boolean disableCull;

    ItemIconRenderProfile(Sampling sampling, boolean disableCull) {
        this.sampling = sampling;
        this.disableCull = disableCull;
    }

    static ItemIconRenderProfile forStack(ItemStack stack) {
        return select(
            GameData.getItemRegistry()
                .getNameForObject(stack.getItem()),
            stack.getItemDamage());
    }

    static ItemIconRenderProfile select(String registryName, int damage) {
        return "gregtech:gt.metaitem.01".equals(registryName) && (damage == 32415 || damage == 32417) ? TESSERACT
            : DEFAULT;
    }

    /** A wall-clock capture schedule; ordinary texture-atlas animation keeps its existing path. */
    static final class Sampling {

        final int frameCount;
        final int frameSize;
        final int intervalMillis;

        Sampling(int frameCount, int frameSize, int intervalMillis) {
            this.frameCount = frameCount;
            this.frameSize = frameSize;
            this.intervalMillis = intervalMillis;
        }
    }
}
