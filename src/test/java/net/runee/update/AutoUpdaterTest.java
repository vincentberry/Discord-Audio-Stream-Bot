package net.runee.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AutoUpdaterTest {
    @Test
    void compareVersionTagsUsesNumericOrdering() {
        assertTrue(AutoUpdater.compareVersionTags("v1.0.10", "v1.0.2") > 0);
        assertTrue(AutoUpdater.compareVersionTags("v2.0.0", "v1.9.9") > 0);
        assertTrue(AutoUpdater.compareVersionTags("v1.0.0", "v1.0.1") < 0);
        assertEquals(0, AutoUpdater.compareVersionTags("v1.2.0", "1.2"));
    }

    @Test
    void matchesNativeAssetForOsClassifier() {
        assertTrue(AutoUpdater.matchesAssetName("Discord Audio Stream Bot-native-windows-x64.zip", "windows-x64"));
        assertFalse(AutoUpdater.matchesAssetName("Discord Audio Stream Bot-portable-windows-x64.zip", "windows-x64"));
        assertFalse(AutoUpdater.matchesAssetName("Discord Audio Stream Bot-native-linux-x64.zip", "windows-x64"));
    }

    @Test
    void selectAssetPrefersNativeClassifier() {
        AutoUpdater.ReleaseAsset portable = new AutoUpdater.ReleaseAsset("Discord Audio Stream Bot-portable-windows-x64.zip", "portable");
        AutoUpdater.ReleaseAsset linux = new AutoUpdater.ReleaseAsset("Discord Audio Stream Bot-native-linux-x64.zip", "linux");
        AutoUpdater.ReleaseAsset windows = new AutoUpdater.ReleaseAsset("Discord Audio Stream Bot-native-windows-x64.zip", "windows");

        assertSame(windows, AutoUpdater.selectAsset(Arrays.asList(portable, linux, windows), "windows-x64"));
    }

    @Test
    void resolvesNativeAndPortableInstallRoots(@TempDir Path tempDir) throws IOException {
        Path nativeRoot = tempDir.resolve("Discord Audio Stream Bot");
        Path nativeJar = nativeRoot.resolve("app").resolve("app.jar");
        Files.createDirectories(nativeJar.getParent());
        Files.createFile(nativeJar);
        assertEquals(nativeRoot.toFile(), AutoUpdater.resolveInstallRoot(nativeJar.toFile()));

        Path portableRoot = tempDir.resolve("portable");
        Path portableJar = portableRoot.resolve("app.jar");
        Files.createDirectories(portableRoot);
        Files.createFile(portableJar);
        assertEquals(portableRoot.toFile(), AutoUpdater.resolveInstallRoot(portableJar.toFile()));

        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        assertNull(AutoUpdater.resolveInstallRoot(classesDir.toFile()));
    }
}
