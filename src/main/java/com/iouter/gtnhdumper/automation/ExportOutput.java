package com.iouter.gtnhdumper.automation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import com.iouter.gtnhdumper.common.utils.ModFilter;

/** Chooses an export directory and replaces only the data selected for this run. */
final class ExportOutput {

    private static final String[] DATA_FILES = { "advitempanel.json", "oreDictionary.json", "fluid.json",
        "gtmaterial.json", "gtorevein.json", "gtsmallorevein.json", "gtundergroundfluid.json", "gtnhdimension.json",
        "ticmaterial.json", "tc4research.json", "quest.json", "questline.json", "recipes_filter_report.json",
        "structure-failures.json" };

    final Path runDirectory;
    final Path outputDirectory;
    private final boolean latest;

    private ExportOutput(Path runDirectory, Path outputDirectory, boolean latest) {
        this.runDirectory = runDirectory;
        this.outputDirectory = outputDirectory;
        this.latest = latest;
    }

    static ExportOutput create(Path gameDirectory, String runId, boolean latest, ModFilter filter) throws IOException {
        Path game = gameDirectory.toAbsolutePath()
            .normalize();
        checkDirectories(game);
        Path runs = game.resolve("dumper-runs");
        createDirectory(runs);
        if (!latest && "latest".equalsIgnoreCase(runId)) {
            throw new IOException("The run ID 'latest' is reserved for reusable output");
        }

        Path base = runs.resolve(latest ? "latest" : runId)
            .normalize();
        if (!runs.equals(base.getParent())) throw new IOException("Export run must be a direct child of dumper-runs");
        createDirectory(base);
        // External runners can prepare environment.json and cached icons before the session claims the run.
        if (!latest) rejectUsedRun(base);

        Path run = latest ? base.resolve(filter.relativeDirectory())
            .normalize() : base;
        // Timestamped runs keep metadata at their common root, preserving the runner's run-ID contract.
        Path output = latest ? run.resolve("dumps")
            : base.resolve("dumps")
                .resolve(filter.relativeDirectory())
                .normalize();
        if (!output.startsWith(base)) throw new IOException("Mod filter directory leaves the export run");
        Path current = base;
        for (Path segment : base.relativize(output)) {
            current = current.resolve(segment);
            createDirectory(current);
        }
        checkExistingFile(run.resolve("request.json"));
        checkExistingFile(run.resolve("status.json"));
        checkExistingFile(run.resolve("status.tmp"));
        checkExistingFile(run.resolve("recipe-scan-failures.json"));
        return new ExportOutput(run, output, latest);
    }

    /** Clear after NEI preparation succeeds; cached images remain available for data-only refreshes. */
    void clearData(boolean layoutsOnly, boolean imagesOnly, boolean smoke) throws IOException {
        if (!latest || imagesOnly) return;
        checkDirectories(outputDirectory);
        List<Path> deletionOrder = new ArrayList<>();
        if (layoutsOnly) {
            collectForDeletion(outputDirectory.resolve("nei-layouts"), true, deletionOrder);
        } else if (smoke) {
            collectForDeletion(outputDirectory.resolve("ticmaterial.json"), false, deletionOrder);
        } else {
            collectForDeletion(outputDirectory.resolve("recipes"), true, deletionOrder);
            collectForDeletion(outputDirectory.resolve("structures"), true, deletionOrder);
            for (String name : DATA_FILES) {
                collectForDeletion(outputDirectory.resolve(name), false, deletionOrder);
            }
        }
        // Preflight every selected tree before removing anything, so a redirected descendant preserves the old data.
        for (Path path : deletionOrder) {
            checkDirectories(path.getParent());
            checkPath(path, Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
            Files.delete(path);
        }
    }

    private void collectForDeletion(Path target, boolean directory, List<Path> deletionOrder) throws IOException {
        if (!target.startsWith(outputDirectory) || target.equals(outputDirectory)) {
            throw new IOException("Cleanup target leaves the export data directory");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        BasicFileAttributes attributes = Files
            .readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        checkPath(target, attributes);
        if (attributes.isDirectory() != directory) throw new IOException("Unexpected export data type: " + target);
        if (!directory) {
            deletionOrder.add(target);
            return;
        }
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                checkPath(dir, attrs);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                checkPath(file, attrs);
                deletionOrder.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                deletionOrder.add(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void createDirectory(Path directory) throws IOException {
        checkDirectories(directory.getParent());
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory);
        checkDirectories(directory);
    }

    private static void checkDirectories(Path directory) throws IOException {
        Path current = directory.getRoot();
        for (Path segment : directory) {
            current = current.resolve(segment);
            BasicFileAttributes attributes = Files
                .readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            checkPath(current, attributes);
            if (!attributes.isDirectory()) throw new IOException("Export path is not a directory: " + current);
        }
    }

    private static void checkExistingFile(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        BasicFileAttributes attributes = Files
            .readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        checkPath(file, attributes);
        if (!attributes.isRegularFile()) throw new IOException("Export metadata is not a regular file: " + file);
    }

    private static void rejectUsedRun(Path directory) throws IOException {
        if (Files.exists(directory.resolve("request.json"), LinkOption.NOFOLLOW_LINKS)
            || Files.exists(directory.resolve("status.json"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Export run ID already used: " + directory);
        }
    }

    private static void checkPath(Path path, BasicFileAttributes attributes) throws IOException {
        // Windows junctions can be reported as special files; real-path comparison also catches redirected parents.
        if (attributes.isSymbolicLink() || attributes.isOther()
            || !path.toRealPath()
                .equals(path))
            throw new IOException("Export path is redirected or special: " + path);
    }
}
