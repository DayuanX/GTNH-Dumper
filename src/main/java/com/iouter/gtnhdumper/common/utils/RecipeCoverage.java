package com.iouter.gtnhdumper.common.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit scope limitations, separate from failures while reading supported recipes. */
public final class RecipeCoverage {

    private static final Map<String, String> unsupported = new LinkedHashMap<>();

    private RecipeCoverage() {}

    public static void unsupported(String source, String reason) {
        unsupported.put(source, reason);
    }

    public static Map<String, String> unsupported() {
        return new LinkedHashMap<>(unsupported);
    }
}
