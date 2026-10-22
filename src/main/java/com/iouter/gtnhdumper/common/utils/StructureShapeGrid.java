package com.iouter.gtnhdumper.common.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

/** Reconstruct dense shape rows without interning the bounding box's empty coordinates. */
public final class StructureShapeGrid {

    private StructureShapeGrid() {}

    public static String[][] decode(Map<Vec3Impl, Character> blocks, Set<Vec3Impl> occupied) {
        int maxA = 0, maxB = 0, maxC = 0;
        for (Vec3Impl position : blocks.keySet()) {
            maxA = Math.max(maxA, position.get0());
            maxB = Math.max(maxB, position.get1());
            maxC = Math.max(maxC, position.get2());
        }
        for (Vec3Impl position : occupied) {
            maxA = Math.max(maxA, position.get0());
            maxB = Math.max(maxB, position.get1());
            maxC = Math.max(maxC, position.get2());
        }
        char[] blank = new char[maxA + 1];
        Arrays.fill(blank, ' ');
        Map<Long, char[]> rows = new HashMap<>();
        // Occupied positions without a block character are controller markers erased by StructureLib's compiler.
        for (Vec3Impl position : occupied) row(rows, position, blank)[position.get0()] = '~';
        blocks.forEach((position, symbol) -> row(rows, position, blank)[position.get0()] = symbol);
        String emptyRow = new String(blank);
        String[][] decoded = new String[maxC + 1][maxB + 1];
        for (int c = 0; c <= maxC; c++) {
            for (int b = 0; b <= maxB; b++) {
                char[] cells = rows.remove(rowKey(b, c));
                decoded[c][b] = cells == null ? emptyRow : new String(cells);
            }
        }
        return decoded;
    }

    private static char[] row(Map<Long, char[]> rows, Vec3Impl position, char[] blank) {
        return rows.computeIfAbsent(rowKey(position.get1(), position.get2()), key -> blank.clone());
    }

    private static long rowKey(int b, int c) {
        return ((long) c << 32) | (b & 0xffffffffL);
    }
}
