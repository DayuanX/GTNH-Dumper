package com.iouter.gtnhdumper.common.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Owns PNG encoding only; callers keep all OpenGL operations on the client thread. */
public final class PngWriter implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final LongAdder count = new LongAdder();
    private final LongAdder encodeNanos = new LongAdder();

    public PngWriter(int workers) {
        if (workers < 0 || workers > 4) throw new IllegalArgumentException("PNG workers must be 0..4");
        executor = workers == 0 ? null
            : new ThreadPoolExecutor(workers, workers, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), task -> {
                Thread thread = new Thread(task, "DumperPngWriter");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** The caller transfers ownership of an immutable image until close() has finished. */
    public void write(File file, BufferedImage image) {
        checkFailure();
        Runnable task = () -> {
            long start = System.nanoTime();
            try {
                encode(file, image);
                count.increment();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                encodeNanos.add(System.nanoTime() - start);
            }
        };
        // Large animation strips can each occupy hundreds of MB; do not retain several in the queue.
        if (executor == null || (long) image.getWidth() * image.getHeight() > 4L * 1024 * 1024) {
            task.run();
            checkFailure();
        } else {
            // A full queue applies backpressure instead of retaining an unbounded number of animation strips.
            executor.execute(task);
        }
    }

    public long count() {
        return count.sum();
    }

    public long encodeNanos() {
        return encodeNanos.sum();
    }

    private void checkFailure() {
        if (failure.get() != null) throw new IllegalStateException("PNG output failed", failure.get());
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.HOURS))
                    throw new IllegalStateException("Timed out waiting for PNG writers");
            } catch (InterruptedException error) {
                Thread.currentThread()
                    .interrupt();
                throw new IllegalStateException("Interrupted while waiting for PNG writers", error);
            }
        }
        checkFailure();
    }

    public static void encode(File file, BufferedImage image) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
            throw new IOException("Cannot create PNG directory: " + parent);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png")
            .next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType(param.getCompressionTypes()[0]);
        // Keep maximum compression, including in the parallel export path.
        param.setCompressionQuality(0f);
        // FileImageOutputStream does not truncate old files; replace the directory entry
        // so shorter rewrites have no trailing bytes and cached hardlinks remain immutable.
        java.nio.file.Files.deleteIfExists(file.toPath());
        try (ImageOutputStream output = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
