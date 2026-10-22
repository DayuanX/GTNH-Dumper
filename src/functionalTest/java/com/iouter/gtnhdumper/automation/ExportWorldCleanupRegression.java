package com.iouter.gtnhdumper.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Filesystem-only checks: cleanup stays inside its target and rejects redirected descendants. */
public final class ExportWorldCleanupRegression {

    private ExportWorldCleanupRegression() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("dumper-cleanup-test")
            .toRealPath();
        Path owned = Files.createDirectory(root.resolve("Dumper-test"));
        Path other = Files.createDirectory(root.resolve("Existing-world"));
        Path preserved = Files.write(other.resolve("level.dat"), new byte[] { 1, 2, 3 });
        try {
            Files.createDirectories(owned.resolve("region"));
            Files.write(owned.resolve("region/r.0.0.mca"), new byte[] { 4 });
            ExportSession.deleteOwnedWorld(owned);
            check(!Files.exists(owned), "Owned world removed");
            check(Files.size(preserved) == 3, "Other world preserved");

            Files.createDirectory(owned);
            Path retained = Files.write(owned.resolve("level.dat"), new byte[] { 5 });
            Path link = owned.resolve("redirect");
            try {
                Files.createSymbolicLink(link, other);
            } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
                System.out.println("Symlink regression skipped: platform does not permit symlink creation");
                return;
            }
            try {
                ExportSession.deleteOwnedWorld(owned);
                throw new AssertionError("Redirected descendant accepted");
            } catch (IOException expected) {
                check(Files.exists(retained), "Preflight prevents partial deletion for rejected paths");
                check(Files.size(preserved) == 3, "Symlink target preserved");
            } finally {
                Files.delete(link);
            }
            System.out.println("Export world cleanup regression passed");
        } finally {
            if (Files.exists(owned)) ExportSession.deleteOwnedWorld(owned);
            Files.delete(preserved);
            Files.delete(other);
            Files.delete(root);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
