package com.iouter.gtnhdumper.automation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.iouter.gtnhdumper.common.utils.ModFilter;

/** Filesystem checks use synthetic mod IDs and require no installed addons or running game. */
public final class ExportOutputRegression {

    private ExportOutputRegression() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("dumper-output-test")
            .toRealPath();
        try {
            checkReusableOutput(Files.createDirectory(root.resolve("reusable")));
            checkTimestampedOutput(Files.createDirectory(root.resolve("timestamped")));
            checkTaskScopes(Files.createDirectory(root.resolve("scopes")));
            checkRedirectedPaths(Files.createDirectory(root.resolve("redirects")));
            System.out.println("Export output regression checks passed");
        } finally {
            deleteTestDirectory(root);
        }
    }

    private static void checkReusableOutput(Path game) throws IOException {
        ExportOutput all = ExportOutput.create(game, "first", true, filter(""));
        Path oldRecipe = write(all.outputDirectory, "recipes/gregtech/old.json", new byte[] { 1 });
        Path oldStructure = write(all.outputDirectory, "structures/old.json", new byte[] { 2 });
        Path oldItems = write(all.outputDirectory, "advitempanel.json", new byte[] { 3 });
        byte[] imageBytes = { 4, 5, 6 };
        Path image = write(all.outputDirectory, "icons/icon.png", imageBytes);
        Path iconIndex = write(all.outputDirectory, "itemicon.json", imageBytes);
        Path imageMap = write(all.outputDirectory, "image-redirects.json", imageBytes);
        Path unrelated = write(all.outputDirectory, "notes.txt", imageBytes);
        ExportOutput addonA = ExportOutput.create(game, "addon-a", true, filter("example.mod"));
        ExportOutput addonB = ExportOutput.create(game, "addon-b", true, filter("otheraddon"));
        Path recipeA = write(addonA.outputDirectory, "recipes/old.json", new byte[] { 7 });
        Path recipeB = write(addonB.outputDirectory, "recipes/old.json", new byte[] { 8 });
        check(
            addonA.runDirectory.equals(game.resolve("dumper-runs/latest/filtered/example%002emod")),
            "Filtered run uses the escaped mod ID");

        ExportOutput repeated = ExportOutput.create(game, "second", true, filter(""));
        check(
            repeated.outputDirectory.equals(all.outputDirectory),
            "Latest output reuses its directory across run IDs");
        repeated.clearData(false, false, false);
        check(!Files.exists(oldRecipe) && !Files.exists(oldStructure) && !Files.exists(oldItems), "Old data removed");
        for (Path preserved : new Path[] { image, iconIndex, imageMap, unrelated }) {
            check(Arrays.equals(Files.readAllBytes(preserved), imageBytes), "Retained file bytes: " + preserved);
        }
        check(Files.exists(recipeA) && Files.exists(recipeB), "Full refresh preserves each filtered output");
        Path newRecipe = write(all.outputDirectory, "recipes/new.json", new byte[] { 9 });
        addonA.clearData(false, false, false);
        check(!Files.exists(recipeA), "Filtered refresh removes its stale recipe files");
        check(Files.exists(newRecipe) && Files.exists(recipeB), "Filtered refresh preserves full and other mod data");
    }

    private static void checkTimestampedOutput(Path game) throws IOException {
        Path prepared = game.resolve("dumper-runs/run-1");
        Path environment = write(prepared, "environment.json", new byte[] { 2 });
        ExportOutput first = ExportOutput.create(game, "run-1", false, filter("example.mod"));
        check(Files.exists(environment), "Runner-prepared directory is accepted and preserved");
        check(first.runDirectory.equals(prepared), "Timestamped run metadata uses the common run root");
        check(
            first.outputDirectory.equals(prepared.resolve("dumps/filtered/example%002emod")),
            "Timestamped filtered data stays under dumps");
        Path data = write(first.outputDirectory, "recipes/retained.json", new byte[] { 1 });
        first.clearData(false, false, false);
        check(Files.exists(data), "Timestamped snapshots are never cleared");
        write(first.runDirectory, "request.json", new byte[] { 3 });
        expectRejected(
            () -> ExportOutput.create(game, "run-1", false, filter("example.mod")),
            "Previously claimed run directory accepted");
        expectRejected(
            () -> ExportOutput.create(game, "run-1", false, filter("otheraddon")),
            "Used run ID accepted for a different mod");
        expectRejected(
            () -> ExportOutput.create(game, "run-1", false, filter("")),
            "Used run ID accepted for full export");
        ExportOutput second = ExportOutput.create(game, "run-2", false, filter(""));
        check(!first.outputDirectory.equals(second.outputDirectory), "Timestamped runs have separate data directories");
        write(second.runDirectory, "status.json", new byte[] { 4 });
        expectRejected(
            () -> ExportOutput.create(game, "run-2", false, filter("otheraddon")),
            "Used base run ID accepted");
        expectRejected(() -> ExportOutput.create(game, "latest", false, filter("")), "Reserved latest ID accepted");
        expectRejected(
            () -> ExportOutput.create(game, "LATEST", false, filter("")),
            "Case variant of reserved ID accepted");
    }

    private static void checkTaskScopes(Path game) throws IOException {
        ExportOutput output = ExportOutput.create(game, "scope", true, filter(""));
        Path recipe = write(output.outputDirectory, "recipes/a.json", new byte[] { 1 });
        Path layout = write(output.outputDirectory, "nei-layouts/layouts.json", new byte[] { 2 });
        Path materials = write(output.outputDirectory, "ticmaterial.json", new byte[] { 3 });
        Path image = write(output.outputDirectory, "icons/a.png", new byte[] { 4 });
        output.clearData(false, true, false);
        check(Files.exists(recipe) && Files.exists(layout) && Files.exists(materials), "Image-only refresh keeps data");
        output.clearData(true, false, false);
        check(!Files.exists(layout), "Layout-only refresh clears layouts");
        check(
            Files.exists(recipe) && Files.exists(materials) && Files.exists(image),
            "Layout refresh stays in its scope");
        layout = write(output.outputDirectory, "nei-layouts/layouts.json", new byte[] { 2 });
        output.clearData(false, false, true);
        check(!Files.exists(materials), "Smoke refresh clears its material JSON");
        check(Files.exists(recipe) && Files.exists(layout) && Files.exists(image), "Smoke refresh stays in its scope");
        output.clearData(false, false, false);
        check(
            Files.exists(layout) && Files.exists(image),
            "Data refresh preserves separately exported layouts and icons");
    }

    private static void checkRedirectedPaths(Path root) throws IOException {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path preserved = write(outside, "data.json", new byte[] { 1, 2, 3 });
        Path game = Files.createDirectory(root.resolve("game"));
        Path redirectedRuns = game.resolve("dumper-runs");
        try {
            Files.createSymbolicLink(redirectedRuns, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            System.out.println("Output symlink checks skipped: platform does not permit symlink creation");
            return;
        }
        expectRejected(() -> ExportOutput.create(game, "test", true, filter("")), "Redirected run root accepted");
        check(!Files.exists(outside.resolve("latest")), "Redirected root has no new output directories");
        Files.delete(redirectedRuns);
        ExportOutput output = ExportOutput.create(game, "test", true, filter(""));
        Path oldRecipe = write(output.outputDirectory, "recipes/old.json", new byte[] { 4 });
        Path items = write(output.outputDirectory, "advitempanel.json", new byte[] { 5 });
        Path redirectedRecipe = output.outputDirectory.resolve("recipes/redirect");
        Files.createSymbolicLink(redirectedRecipe, outside);
        expectRejected(() -> output.clearData(false, false, false), "Redirected recipe descendant accepted");
        check(Files.exists(oldRecipe) && Files.exists(items), "Preflight preserves old data when a tree is rejected");
        check(Files.size(preserved) == 3, "Redirect target preserved");
        Files.delete(redirectedRecipe);
        Path metadata = output.runDirectory.resolve("status.json");
        Files.createSymbolicLink(metadata, preserved);
        expectRejected(() -> ExportOutput.create(game, "retry", true, filter("")), "Redirected metadata accepted");
        Files.delete(metadata);
    }

    private static ModFilter filter(String input) {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("example.mod", "Example Addon");
        mods.put("otheraddon", "Other Addon");
        return new ModFilter(input, mods);
    }

    private static Path write(Path directory, String relativePath, byte[] bytes) throws IOException {
        Path file = directory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.write(file, bytes);
    }

    private static void expectRejected(IOAction action, String message) throws IOException {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            // Each case supplies an existing or redirected path that must fail before export.
        }
    }

    private static void deleteTestDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface IOAction {

        void run() throws IOException;
    }
}
