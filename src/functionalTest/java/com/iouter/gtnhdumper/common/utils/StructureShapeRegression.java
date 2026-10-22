package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

/** Large sparse bounds must not populate StructureLib's permanent coordinate pool. */
public final class StructureShapeRegression {

    private StructureShapeRegression() {}

    public static void main(String[] args) {
        try {
            Field poolField = Vec3Impl.class.getDeclaredField("pool");
            poolField.setAccessible(true);
            Map<?, ?> pool = (Map<?, ?>) poolField.get(null);
            int before = pool.size();
            Map<Vec3Impl, Character> blocks = new HashMap<>();
            blocks.put(new Vec3Impl(256, 256, 256), 'A');
            blocks.put(new Vec3Impl(2, 1, 0), 'B');
            Set<Vec3Impl> occupied = new HashSet<>(blocks.keySet());
            occupied.add(new Vec3Impl(1, 1, 0));
            String[][] decoded = StructureShapeGrid.decode(blocks, occupied);
            check(decoded.length == 257 && decoded[0].length == 257, "Slice/row dimensions");
            check(decoded[256][256].length() == 257 && decoded[256][256].charAt(256) == 'A', "Far corner block");
            check(
                decoded[0][1].substring(0, 4)
                    .equals(" ~B "),
                "Controller and ordinary block precedence");
            check(decoded[0][0] == decoded[1][0], "Empty rows share immutable storage");
            check(pool.size() == before, "No bounding-box positions interned globally");
            blocks.clear();
            occupied.clear();
            occupied.add(new Vec3Impl(3, 2, 1));
            check(StructureShapeGrid.decode(blocks, occupied)[1][2].equals("   ~"), "Controller-only shape");
            System.out.println("Structure shape regression passed");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
