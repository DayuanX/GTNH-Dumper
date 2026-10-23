package com.iouter.gtnhdumper.automation;

import com.google.gson.JsonObject;

/** Export scope selected by the one-shot request, independent of the NEI options screen. */
final class ExportOptions {

    final String outputMode;
    final String modFilter;
    final boolean smoke;
    final boolean layoutsOnly;
    final boolean imagesOnly;
    final boolean noImages;

    ExportOptions(JsonObject request) {
        outputMode = request.has("outputMode") ? request.get("outputMode")
            .getAsString() : "timestamp";
        if (!outputMode.equals("timestamp") && !outputMode.equals("latest")) {
            throw new IllegalArgumentException("outputMode must be timestamp or latest");
        }
        modFilter = request.has("modFilter") ? request.get("modFilter")
            .getAsString()
            .trim() : "";
        smoke = flag(request, "smoke", false);
        layoutsOnly = flag(request, "layoutsOnly", false);
        imagesOnly = flag(request, "imagesOnly", false);
        if (imagesOnly && (layoutsOnly || smoke)) {
            throw new IllegalArgumentException("imagesOnly excludes layoutsOnly/smoke");
        }
        // These modes explicitly request image capture, including with the generated noImages=true default.
        noImages = flag(request, "noImages", true) && !imagesOnly && !layoutsOnly;
        if (!modFilter.isEmpty() && (layoutsOnly || smoke)) {
            throw new IllegalArgumentException(
                "Mod filtering requires a data or imagesOnly export; layouts and smoke data have no recipe-owner filter");
        }
    }

    boolean latest() {
        return outputMode.equals("latest");
    }

    private static boolean flag(JsonObject request, String name, boolean fallback) {
        return request.has(name) ? request.get(name)
            .getAsBoolean() : fallback;
    }
}
