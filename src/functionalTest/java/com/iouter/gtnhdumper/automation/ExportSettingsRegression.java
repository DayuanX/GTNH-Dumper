package com.iouter.gtnhdumper.automation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.EnumDifficulty;

import com.google.gson.JsonObject;

/** First-launch configuration and restoration checks run without a Minecraft client or addon mods. */
public final class ExportSettingsRegression {

    private ExportSettingsRegression() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("dumper-settings-test");
        Path config = root.resolve("config/gtnhdumper-autorun.json");
        try {
            JsonObject defaults = ExportSession.readRequest(config, new com.google.gson.Gson());
            check(
                !defaults.get("enabled")
                    .getAsBoolean(),
                "First launch must leave export disabled");
            check(
                defaults.get("exitGame")
                    .getAsBoolean(),
                "Default request preserves shutdown behavior");
            check(
                defaults.get("seed")
                    .getAsLong() == 290002L,
                "Default seed matches export settings");
            check(
                defaults.get("noImages")
                    .getAsBoolean(),
                "Default export skips item image rendering");
            check(
                defaults.get("outputMode")
                    .getAsString()
                    .equals("timestamp"),
                "Default output retains per-run directories");
            check(
                defaults.get("modFilter")
                    .getAsString()
                    .isEmpty(),
                "Default export includes all mods");
            check(new ExportOptions(defaults).noImages, "Generated settings and omitted settings share image defaults");
            check(
                !defaults.has("runId") && !defaults.has("imageKeys") && !defaults.has("imageMetadata"),
                "Generated IDs and optional filters must stay absent");
            for (String flag : new String[] { "keepWorld", "smoke", "layoutsOnly", "imagesOnly", "imageDiagnostics" }) {
                check(
                    !defaults.get(flag)
                        .getAsBoolean(),
                    "Default flag: " + flag);
            }
            byte[] custom = "{\"enabled\":true,\"seed\":42,\"exitGame\":false}".getBytes(StandardCharsets.UTF_8);
            Files.write(config, custom);
            JsonObject existing = ExportSession.readRequest(config, new com.google.gson.Gson());
            check(
                existing.get("enabled")
                    .getAsBoolean()
                    && existing.get("seed")
                        .getAsInt() == 42,
                "Existing requests remain available for one-shot consumption");
            check(Arrays.equals(custom, Files.readAllBytes(config)), "Existing configuration stays unchanged");
        } finally {
            Files.deleteIfExists(config);
            Files.deleteIfExists(config.getParent());
            Files.delete(root);
        }

        checkOptions();

        for (boolean pause : new boolean[] { true, false }) {
            for (EnumDifficulty difficulty : EnumDifficulty.values()) {
                RecordingSettings settings = new RecordingSettings();
                settings.pauseOnLostFocus = pause;
                settings.difficulty = difficulty;
                ExportSession.ClientSettings snapshot = new ExportSession.ClientSettings(settings);
                settings.pauseOnLostFocus = false;
                settings.difficulty = EnumDifficulty.PEACEFUL;
                settings.renderDistanceChunks = 7;
                snapshot.restore(settings);
                check(
                    settings.pauseOnLostFocus == pause && settings.difficulty == difficulty,
                    "Restore both settings across every original difficulty/focus combination");
                check(
                    settings.savedPause == pause && settings.savedDifficulty == difficulty,
                    "Persist original settings after temporary values may have been saved");
                check(settings.renderDistanceChunks == 7, "Preserve unrelated settings");
            }
        }
        System.out.println("Export configuration and settings regression passed");
    }

    private static void checkOptions() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        ExportOptions defaults = new ExportOptions(new JsonObject());
        check(defaults.noImages && !defaults.latest(), "Minimal requests update data without rendering icons");
        ExportOptions latest = new ExportOptions(
            gson.fromJson("{\"outputMode\":\"latest\",\"modFilter\":\" Example Addon \"}", JsonObject.class));
        check(latest.latest() && latest.modFilter.equals("Example Addon"), "Fixed output and trimmed mod selection");
        ExportOptions images = new ExportOptions(gson.fromJson("{\"noImages\":false}", JsonObject.class));
        check(!images.noImages, "Full image export remains opt-in");
        for (String mode : new String[] { "imagesOnly", "layoutsOnly" }) {
            JsonObject request = new JsonObject();
            request.addProperty("noImages", true);
            request.addProperty(mode, true);
            check(
                !new ExportOptions(request).noImages,
                "Explicit capture mode overrides generated noImages default: " + mode);
        }
        ExportOptions filteredImages = new ExportOptions(
            gson.fromJson("{\"imagesOnly\":true,\"modFilter\":\"exampleaddon\"}", JsonObject.class));
        check(!filteredImages.noImages, "Filtered image-only exports retain capture");
        for (String invalid : new String[] { "{\"outputMode\":\"../elsewhere\"}",
            "{\"imagesOnly\":true,\"layoutsOnly\":true}", "{\"imagesOnly\":true,\"smoke\":true}",
            "{\"modFilter\":\"exampleaddon\",\"layoutsOnly\":true}",
            "{\"modFilter\":\"exampleaddon\",\"smoke\":true}" }) {
            try {
                new ExportOptions(gson.fromJson(invalid, JsonObject.class));
                throw new AssertionError("Expected invalid autorun options: " + invalid);
            } catch (IllegalArgumentException expected) {
                // Reject unsupported combinations before creating a world or clearing prior output.
            }
        }
    }

    private static final class RecordingSettings extends GameSettings {

        private boolean savedPause;
        private EnumDifficulty savedDifficulty;

        @Override
        public void saveOptions() {
            savedPause = pauseOnLostFocus;
            savedDifficulty = difficulty;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
