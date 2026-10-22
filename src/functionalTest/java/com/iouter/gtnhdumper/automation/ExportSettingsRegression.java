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
