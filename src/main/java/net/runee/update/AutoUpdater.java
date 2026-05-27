package net.runee.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runee.DiscordAudioStreamBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AutoUpdater {
    private static final Logger logger = LoggerFactory.getLogger(AutoUpdater.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = DiscordAudioStreamBot.NAME.replace(' ', '-') + "-Updater";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/" + DiscordAudioStreamBot.GITHUB_REPOSITORY + "/releases/latest";
    private static final String UPDATE_DIRECTORY = ".updates";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static volatile boolean checkStarted;

    private AutoUpdater() {
    }

    public static void checkForUpdatesInBackground(Component parent) {
        if (!DiscordAudioStreamBot.getConfig().isAutoUpdate()) {
            logger.info("Auto-update is disabled");
            return;
        }
        if (checkStarted) {
            return;
        }
        checkStarted = true;

        Thread thread = new Thread(() -> {
            try {
                checkForUpdates(parent);
            } catch (Exception ex) {
                logger.warn("Auto-update check failed", ex);
            }
        });
        thread.setName("DASB Auto Updater");
        thread.setDaemon(true);
        thread.start();
    }

    private static void checkForUpdates(Component parent) throws IOException, InterruptedException {
        UpdatePlan plan = findUpdate();
        if (plan == null) {
            return;
        }

        logger.info("Update available: {} -> {}", plan.currentVersion, plan.release.tagName);
        StagedUpdate stagedUpdate = stageUpdate(plan);
        promptInstall(parent, plan, stagedUpdate);
    }

    private static UpdatePlan findUpdate() throws IOException, InterruptedException {
        String currentVersion = getCurrentVersionTag();
        if (!isPackagedReleaseVersion(currentVersion)) {
            logger.info("Skipping auto-update for development version: {}", currentVersion != null ? currentVersion : "unknown");
            return null;
        }

        File installRoot = resolveInstallRoot(getApplicationLocation());
        Launcher launcher = installRoot != null ? resolveLauncher(installRoot) : null;
        if (installRoot == null || launcher == null) {
            logger.info("Skipping auto-update because this launch does not look like a packaged app");
            return null;
        }

        String osClassifier = currentOsClassifier();
        if (osClassifier == null) {
            logger.info("Skipping auto-update on unsupported OS: {}", System.getProperty("os.name"));
            return null;
        }

        Release release = fetchLatestRelease();
        if (release == null || release.tagName == null) {
            logger.warn("Latest GitHub release did not include a tag name");
            return null;
        }
        if (release.prerelease) {
            logger.info("Skipping prerelease update {}", release.tagName);
            return null;
        }
        if (compareVersionTags(release.tagName, currentVersion) <= 0) {
            logger.info("Already up to date: {}", currentVersion);
            return null;
        }

        ReleaseAsset asset = selectAsset(release.assets, osClassifier);
        if (asset == null) {
            logger.warn("No release asset matches classifier '{}'", osClassifier);
            return null;
        }

        return new UpdatePlan(currentVersion, osClassifier, installRoot, launcher, release, asset);
    }

    private static Release fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_URL))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GitHub release request failed with HTTP " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Release release = new Release();
        release.tagName = getString(json, "tag_name");
        release.htmlUrl = getString(json, "html_url");
        release.prerelease = getBoolean(json, "prerelease");
        release.assets = new ArrayList<>();

        JsonArray assets = json.getAsJsonArray("assets");
        if (assets != null) {
            for (JsonElement element : assets) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject assetJson = element.getAsJsonObject();
                ReleaseAsset asset = new ReleaseAsset();
                asset.name = getString(assetJson, "name");
                asset.downloadUrl = getString(assetJson, "browser_download_url");
                asset.size = getLong(assetJson, "size");
                if (asset.name != null && asset.downloadUrl != null) {
                    release.assets.add(asset);
                }
            }
        }
        return release;
    }

    private static StagedUpdate stageUpdate(UpdatePlan plan) throws IOException, InterruptedException {
        Path updatesDir = plan.installRoot.toPath().resolve(UPDATE_DIRECTORY);
        String updateId = sanitizeFileName(plan.release.tagName) + "-" + System.currentTimeMillis();
        Path workDir = updatesDir.resolve(updateId);
        Path extractDir = workDir.resolve("extract");
        Files.createDirectories(extractDir);

        Path archivePath = workDir.resolve(plan.asset.name);
        logger.info("Downloading update asset {}", plan.asset.name);
        download(plan.asset.downloadUrl, archivePath);

        logger.info("Extracting update to {}", extractDir);
        unzip(archivePath, extractDir);
        File sourceRoot = findExtractedInstallRoot(extractDir, plan.installRoot.getName());
        return new StagedUpdate(workDir, sourceRoot);
    }

    private static void download(String url, Path destination) throws IOException, InterruptedException {
        Files.createDirectories(destination.getParent());
        Path tempDestination = destination.resolveSibling(destination.getFileName() + ".tmp");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempDestination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(tempDestination);
            throw new IOException("Download failed with HTTP " + response.statusCode());
        }
        Files.move(tempDestination, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void promptInstall(Component parent, UpdatePlan plan, StagedUpdate stagedUpdate) {
        if (GraphicsEnvironment.isHeadless()) {
            logger.info("Update {} is staged at {}. Restart manually to install it.", plan.release.tagName, stagedUpdate.workDir);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String message = "A new version is ready to install.\n\n"
                    + "Current version: " + plan.currentVersion + "\n"
                    + "New version: " + plan.release.tagName + "\n\n"
                    + "Restart and install now?";
            int choice = JOptionPane.showConfirmDialog(parent, message, "Update available", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                logger.info("User postponed update {}", plan.release.tagName);
                return;
            }

            try {
                launchInstaller(plan, stagedUpdate);
                System.exit(0);
            } catch (IOException ex) {
                logger.error("Failed to launch updater", ex);
                JOptionPane.showMessageDialog(parent, "Failed to launch updater:\n" + ex.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void launchInstaller(UpdatePlan plan, StagedUpdate stagedUpdate) throws IOException {
        Path script = createInstallerScript(plan, stagedUpdate);
        long pid = ProcessHandle.current().pid();
        ProcessBuilder processBuilder;
        if (isWindows()) {
            processBuilder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden",
                    "-File", script.toAbsolutePath().toString(),
                    Long.toString(pid)
            );
        } else {
            processBuilder = new ProcessBuilder("/bin/sh", script.toAbsolutePath().toString(), Long.toString(pid));
        }
        processBuilder.directory(plan.installRoot);
        processBuilder.start();
        logger.info("Updater launched for {}", plan.release.tagName);
    }

    private static Path createInstallerScript(UpdatePlan plan, StagedUpdate stagedUpdate) throws IOException {
        Path script = stagedUpdate.workDir.resolve(isWindows() ? "install-update.ps1" : "install-update.sh");
        String content = isWindows()
                ? buildWindowsInstallerScript(plan, stagedUpdate)
                : buildUnixInstallerScript(plan, stagedUpdate);
        Files.writeString(script, content, StandardCharsets.UTF_8);
        if (!isWindows()) {
            script.toFile().setExecutable(true, false);
        }
        return script;
    }

    private static String buildWindowsInstallerScript(UpdatePlan plan, StagedUpdate stagedUpdate) {
        String target = plan.installRoot.getAbsolutePath();
        String source = stagedUpdate.sourceRoot.getAbsolutePath();
        String launcher = plan.launcher.file.getAbsolutePath();
        String workingDirectory = plan.launcher.workingDirectory.getAbsolutePath();
        String logFile = plan.installRoot.toPath().resolve(UPDATE_DIRECTORY).resolve("updater.log").toString();
        return String.join("\r\n",
                "param([int]$AppProcessId)",
                "$ErrorActionPreference = 'Stop'",
                "$source = " + psQuote(source),
                "$target = " + psQuote(target),
                "$launcher = " + psQuote(launcher),
                "$workingDirectory = " + psQuote(workingDirectory),
                "$logFile = " + psQuote(logFile),
                "function Write-UpdaterLog([string]$Message) {",
                "    $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'",
                "    Add-Content -LiteralPath $logFile -Value \"$stamp $Message\"",
                "}",
                "New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logFile) | Out-Null",
                "Write-UpdaterLog \"Waiting for process $AppProcessId\"",
                "Wait-Process -Id $AppProcessId -ErrorAction SilentlyContinue",
                "Start-Sleep -Milliseconds 800",
                "Write-UpdaterLog \"Copying update from $source to $target\"",
                "Get-ChildItem -LiteralPath $source -Force | ForEach-Object {",
                "    Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force",
                "}",
                "Write-UpdaterLog \"Starting $launcher\"",
                "Start-Process -FilePath $launcher -WorkingDirectory $workingDirectory",
                "Write-UpdaterLog \"Update completed\"",
                ""
        );
    }

    private static String buildUnixInstallerScript(UpdatePlan plan, StagedUpdate stagedUpdate) {
        String target = plan.installRoot.getAbsolutePath();
        String source = stagedUpdate.sourceRoot.getAbsolutePath();
        String launcher = plan.launcher.file.getAbsolutePath();
        String workingDirectory = plan.launcher.workingDirectory.getAbsolutePath();
        String logFile = plan.installRoot.toPath().resolve(UPDATE_DIRECTORY).resolve("updater.log").toString();
        List<String> lines = new ArrayList<>();
        lines.add("#!/usr/bin/env sh");
        lines.add("set -eu");
        lines.add("pid=\"$1\"");
        lines.add("source=" + shQuote(source));
        lines.add("target=" + shQuote(target));
        lines.add("launcher=" + shQuote(launcher));
        lines.add("working_directory=" + shQuote(workingDirectory));
        lines.add("log_file=" + shQuote(logFile));
        lines.add("mkdir -p \"$(dirname \"$log_file\")\"");
        lines.add("log() { printf '%s %s\\n' \"$(date '+%Y-%m-%d %H:%M:%S')\" \"$1\" >> \"$log_file\"; }");
        lines.add("log \"Waiting for process $pid\"");
        lines.add("while kill -0 \"$pid\" 2>/dev/null; do sleep 1; done");
        lines.add("sleep 1");
        lines.add("log \"Copying update from $source to $target\"");
        lines.add("cp -R \"$source\"/. \"$target\"/");
        lines.add("chmod +x \"$launcher\" 2>/dev/null || true");
        lines.add("log \"Starting $launcher\"");
        if (plan.launcher.type == LauncherType.MAC_APP) {
            lines.add("nohup open \"$launcher\" >/dev/null 2>&1 &");
        } else {
            lines.add("(cd \"$working_directory\" && nohup \"$launcher\" >/dev/null 2>&1 &)");
        }
        lines.add("log \"Update completed\"");
        lines.add("");
        return String.join("\n", lines);
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) {
                    throw new IOException("Unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static File findExtractedInstallRoot(Path extractDir, String expectedName) throws IOException {
        List<Path> directories = new ArrayList<>();
        try (var stream = Files.list(extractDir)) {
            stream.filter(Files::isDirectory).forEach(directories::add);
        }
        directories.sort(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        for (Path directory : directories) {
            if (directory.getFileName().toString().equals(expectedName)) {
                return directory.toFile();
            }
        }
        if (directories.size() == 1) {
            return directories.get(0).toFile();
        }
        return extractDir.toFile();
    }

    private static File getApplicationLocation() {
        try {
            CodeSource codeSource = AutoUpdater.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            URL location = codeSource.getLocation();
            if (location == null) {
                return null;
            }
            return new File(location.toURI());
        } catch (URISyntaxException | IllegalArgumentException ex) {
            logger.warn("Failed to resolve application location", ex);
            return null;
        }
    }

    static File resolveInstallRoot(File applicationLocation) {
        if (applicationLocation == null || !applicationLocation.isFile()) {
            return null;
        }

        File jarParent = applicationLocation.getParentFile();
        if (jarParent == null) {
            return null;
        }

        if (!applicationLocation.getName().equalsIgnoreCase("app.jar")) {
            return null;
        }

        File parent = jarParent.getParentFile();
        File grandParent = parent != null ? parent.getParentFile() : null;
        if (jarParent.getName().equalsIgnoreCase("app") && parent != null && parent.getName().equals("Contents")
                && grandParent != null && grandParent.getName().endsWith(".app")) {
            return grandParent;
        }
        if (jarParent.getName().equalsIgnoreCase("app") && parent != null && parent.getName().equalsIgnoreCase("lib")
                && grandParent != null) {
            return grandParent;
        }
        if (jarParent.getName().equalsIgnoreCase("app") && parent != null) {
            return parent;
        }

        return jarParent;
    }

    private static Launcher resolveLauncher(File installRoot) {
        if (installRoot == null) {
            return null;
        }
        if (isWindows()) {
            File exe = new File(installRoot, DiscordAudioStreamBot.NAME + ".exe");
            if (exe.isFile()) {
                return new Launcher(LauncherType.EXECUTABLE, exe, installRoot);
            }
            File runBat = new File(installRoot, "run.bat");
            if (runBat.isFile()) {
                return new Launcher(LauncherType.EXECUTABLE, runBat, installRoot);
            }
            return null;
        }

        if (installRoot.getName().endsWith(".app")) {
            return new Launcher(LauncherType.MAC_APP, installRoot, installRoot.getParentFile());
        }

        File runSh = new File(installRoot, "run.sh");
        if (runSh.isFile()) {
            return new Launcher(LauncherType.EXECUTABLE, runSh, installRoot);
        }

        File nativeLauncher = new File(new File(installRoot, "bin"), DiscordAudioStreamBot.NAME);
        if (nativeLauncher.isFile()) {
            return new Launcher(LauncherType.EXECUTABLE, nativeLauncher, installRoot);
        }

        return null;
    }

    static ReleaseAsset selectAsset(List<ReleaseAsset> assets, String osClassifier) {
        if (assets == null) {
            return null;
        }
        for (ReleaseAsset asset : assets) {
            if (matchesAssetName(asset.name, osClassifier)) {
                return asset;
            }
        }
        for (ReleaseAsset asset : assets) {
            String lowerName = asset.name != null ? asset.name.toLowerCase(Locale.ROOT) : "";
            if (lowerName.endsWith(".zip") && lowerName.contains(osClassifier.toLowerCase(Locale.ROOT))) {
                return asset;
            }
        }
        return null;
    }

    static boolean matchesAssetName(String assetName, String osClassifier) {
        if (assetName == null || osClassifier == null) {
            return false;
        }
        String lowerName = assetName.toLowerCase(Locale.ROOT);
        String lowerClassifier = osClassifier.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".zip")
                && lowerName.contains("native-" + lowerClassifier)
                && !lowerName.contains("portable-");
    }

    static int compareVersionTags(String left, String right) {
        List<Integer> leftParts = parseVersionParts(left);
        List<Integer> rightParts = parseVersionParts(right);
        if (leftParts.isEmpty() || rightParts.isEmpty()) {
            return Objects.equals(normalizeTag(left), normalizeTag(right)) ? 0 : 1;
        }

        int max = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < max; i++) {
            int leftPart = i < leftParts.size() ? leftParts.get(i) : 0;
            int rightPart = i < rightParts.size() ? rightParts.get(i) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static List<Integer> parseVersionParts(String tag) {
        String normalized = normalizeTag(tag);
        String[] tokens = normalized.split("[^0-9]+");
        List<Integer> result = new ArrayList<>();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(token));
            } catch (NumberFormatException ex) {
                return new ArrayList<>();
            }
        }
        return result;
    }

    private static String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String normalized = tag.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean isPackagedReleaseVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return false;
        }
        String upper = version.toUpperCase(Locale.ROOT);
        return !upper.contains("SNAPSHOT") && !upper.contains("DEV");
    }

    private static String getCurrentVersionTag() {
        Package pkg = AutoUpdater.class.getPackage();
        return pkg != null ? pkg.getImplementationVersion() : null;
    }

    static String currentOsClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String archClassifier;
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            archClassifier = "x64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            archClassifier = "arm64";
        } else if (arch.equals("x86") || arch.equals("i386") || arch.equals("i486") || arch.equals("i586") || arch.equals("i686")) {
            archClassifier = "x86";
        } else if (arch.startsWith("arm")) {
            archClassifier = "arm";
        } else {
            archClassifier = arch.replaceAll("[^a-z0-9]+", "-");
        }

        if (os.contains("win")) {
            return "windows-" + archClassifier;
        }
        if (os.contains("mac")) {
            return "macos-" + archClassifier;
        }
        if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            return "linux-" + archClassifier;
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

    private static boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static long getLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsLong() : -1L;
    }

    private static String sanitizeFileName(String value) {
        return normalizeTag(value).replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String shQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static class ReleaseAsset {
        String name;
        String downloadUrl;
        long size;

        ReleaseAsset() {
        }

        ReleaseAsset(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }
    }

    private static class Release {
        String tagName;
        String htmlUrl;
        boolean prerelease;
        List<ReleaseAsset> assets;
    }

    private static class UpdatePlan {
        final String currentVersion;
        final String osClassifier;
        final File installRoot;
        final Launcher launcher;
        final Release release;
        final ReleaseAsset asset;

        private UpdatePlan(String currentVersion, String osClassifier, File installRoot, Launcher launcher, Release release, ReleaseAsset asset) {
            this.currentVersion = currentVersion;
            this.osClassifier = osClassifier;
            this.installRoot = installRoot;
            this.launcher = launcher;
            this.release = release;
            this.asset = asset;
        }
    }

    private static class StagedUpdate {
        final Path workDir;
        final File sourceRoot;

        private StagedUpdate(Path workDir, File sourceRoot) {
            this.workDir = workDir;
            this.sourceRoot = sourceRoot;
        }
    }

    private static class Launcher {
        final LauncherType type;
        final File file;
        final File workingDirectory;

        private Launcher(LauncherType type, File file, File workingDirectory) {
            this.type = type;
            this.file = file;
            this.workingDirectory = workingDirectory;
        }
    }

    private enum LauncherType {
        EXECUTABLE,
        MAC_APP
    }
}
