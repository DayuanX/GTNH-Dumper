package com.iouter.gtnhdumper.common.dumper;

import java.awt.image.BufferedImage;

/** Clock-driven captures retain repeated frames to preserve duration and frame positions. */
public final class TimedIconFramesRegression {

    public static void main(String[] args) {
        checkProfiles();
        checkFrameStrip();
    }

    private static void checkProfiles() {
        ItemIconRenderProfile normal = ItemIconRenderProfile.select("minecraft:diamond", 0);
        if (normal.sampling != null || normal.disableCull) throw new AssertionError("Ordinary item policy changed");
        ItemIconRenderProfile raw = ItemIconRenderProfile.select("gregtech:gt.metaitem.01", 32415);
        ItemIconRenderProfile energized = ItemIconRenderProfile.select("gregtech:gt.metaitem.01", 32417);
        if (raw != energized || !raw.disableCull) throw new AssertionError("Tesseract profile mismatch");
        if (raw.sampling.frameCount != 120 || raw.sampling.frameSize != 64 || raw.sampling.intervalMillis != 50)
            throw new AssertionError("Tesseract sampling contract changed");
        if (ItemIconRenderProfile.select("other:item", 32415) != normal
            || ItemIconRenderProfile.select("gregtech:gt.metaitem.01", 32416) != normal)
            throw new AssertionError("Profile must match both item identity and damage");
        // Sampling and GL state are separate requirements, including for static custom renderers.
        ItemIconRenderProfile samplingOnly = new ItemIconRenderProfile(raw.sampling, false);
        ItemIconRenderProfile cullOnly = new ItemIconRenderProfile(null, true);
        if (samplingOnly.sampling == null || samplingOnly.disableCull
            || cullOnly.sampling != null
            || !cullOnly.disableCull) throw new AssertionError("Sampling and culling became coupled");
    }

    private static void checkFrameStrip() {
        BufferedImage[] frames = new BufferedImage[120];
        for (int i = 0; i < frames.length; i++) frames[i] = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        BufferedImage strip = ItemIconDumper.concatenateImages(frames, false);
        if (strip.getWidth() != 7680 || strip.getHeight() != 64) throw new AssertionError("Frame metadata mismatch");
        for (int i = 0; i < frames.length; i++) frames[i].setRGB(0, 0, 0xff000000 | i);
        strip = ItemIconDumper.concatenateImages(frames, false);
        for (int i = 0; i < frames.length; i++)
            if (strip.getRGB(i * 64, 0) != (0xff000000 | i)) throw new AssertionError("Lost or reordered frame " + i);
        BufferedImage[] duplicates = { frames[0], frames[0] };
        if (ItemIconDumper.concatenateImages(duplicates)
            .getWidth() != 64) throw new AssertionError("Existing texture deduplication changed");
    }
}
