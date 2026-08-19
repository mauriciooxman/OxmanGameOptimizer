package cl.oxman.oxmangameoptimizer.game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Discovers installed games from launcher manifests and relates them to running processes. */
public final class GameDiscoveryService {
    private static final Pattern VDF = Pattern.compile("\\\"([^\\\"]+)\\\"\\s+\\\"([^\\\"]*)\\\"");
    private static final Pattern JSON = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    public List<GameProfile> discoverInstalledGames() {
        Map<String, GameProfile> games = new LinkedHashMap<>();
        discoverSteam().forEach(game -> add(games, game));
        discoverEpic().forEach(game -> add(games, game));
        discoverValorant().ifPresent(game -> add(games, game));
        return games.values().stream().sorted(Comparator.comparing(GameProfile::toString,
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Optional<GameProfile> findRunningGame(List<GameProfile> profiles) {
        List<ProcessHandle> processes = ProcessHandle.allProcesses().toList();
        return profiles.stream().filter(profile -> processes.stream().anyMatch(profile::matches)).findFirst();
    }

    private List<GameProfile> discoverSteam() {
        List<GameProfile> result = new ArrayList<>();
        Path steam = Path.of(System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)"), "Steam");
        List<Path> libraries = new ArrayList<>(List.of(steam));
        Path folders = steam.resolve("steamapps/libraryfolders.vdf");
        if (Files.isRegularFile(folders)) try {
            var matcher = VDF.matcher(Files.readString(folders));
            while (matcher.find()) if ("path".equalsIgnoreCase(matcher.group(1))) {
                Path library = Path.of(unescape(matcher.group(2)));
                if (!libraries.contains(library)) libraries.add(library);
            }
        } catch (IOException | RuntimeException ignored) { }

        for (Path library : libraries) {
            Path steamApps = library.resolve("steamapps");
            if (!Files.isDirectory(steamApps)) continue;
            try (var files = Files.list(steamApps)) {
                files.filter(path -> path.getFileName().toString().matches("appmanifest_\\d+\\.acf"))
                        .forEach(path -> parseSteam(path, steamApps.resolve("common")).ifPresent(result::add));
            } catch (IOException ignored) { }
        }
        return result;
    }

    private Optional<GameProfile> parseSteam(Path manifest, Path common) {
        try {
            Map<String, String> values = pairs(Files.readString(manifest, StandardCharsets.UTF_8), VDF);
            String id = values.get("appid"), name = values.get("name"), directory = values.get("installdir");
            if (id == null || name == null || directory == null
                    || !Files.isDirectory(common.resolve(directory))) return Optional.empty();
            if ("730".equals(id)) return Optional.of(GameProfile.COUNTER_STRIKE_2);
            return Optional.of(GameProfile.installed("steam:" + id, name, common.resolve(directory),
                    "steam://rungameid/" + id));
        } catch (IOException ignored) { return Optional.empty(); }
    }

    private List<GameProfile> discoverEpic() {
        List<GameProfile> result = new ArrayList<>();
        Path directory = Path.of(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"),
                "Epic", "EpicGamesLauncher", "Data", "Manifests");
        if (!Files.isDirectory(directory)) return result;
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".item")).forEach(path -> {
                try {
                    Map<String, String> value = pairs(Files.readString(path), JSON);
                    String name = value.get("DisplayName"), location = value.get("InstallLocation");
                    String app = value.get("AppName"), namespace = value.get("CatalogNamespace");
                    if (name != null && location != null && app != null && Files.isDirectory(Path.of(location))) {
                        String uri = namespace == null ? null : "com.epicgames.launcher://apps/" + namespace
                                + "%3A" + app + "%3A" + app + "?action=launch&silent=true";
                        result.add(GameProfile.installed("epic:" + app, name, Path.of(location), uri));
                    }
                } catch (IOException | RuntimeException ignored) { }
            });
        } catch (IOException ignored) { }
        return result;
    }

    private Optional<GameProfile> discoverValorant() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("C:\\Riot Games\\VALORANT"));
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) candidates.add(Path.of(programFiles, "Riot Games", "VALORANT"));

        Path settings = Path.of(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"),
                "Riot Games", "Metadata", "valorant.live", "valorant.live.product_settings.yaml");
        if (Files.isRegularFile(settings)) try {
            for (String line : Files.readAllLines(settings)) {
                String key = "product_install_full_path:";
                if (line.trim().startsWith(key)) {
                    String path = line.trim().substring(key.length()).trim().replace("\"", "");
                    if (!path.isBlank()) candidates.add(Path.of(path));
                    break;
                }
            }
        } catch (IOException | RuntimeException ignored) { }

        return candidates.stream().filter(Files::isDirectory).findFirst().map(path -> new GameProfile(
                "riot:valorant", "VALORANT",
                java.util.Set.of("valorant-win64-shipping.exe", "valorant.exe"), path,
                "riotclient://launch-product=valorant&launch-patchline=live"));
    }

    private static Map<String, String> pairs(String text, Pattern pattern) {
        Map<String, String> result = new LinkedHashMap<>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) result.put(matcher.group(1), unescape(matcher.group(2)));
        return result;
    }

    private static String unescape(String value) { return value.replace("\\\\", "\\").replace("\\\"", "\""); }
    private static void add(Map<String, GameProfile> games, GameProfile profile) {
        games.put(profile.getId().toLowerCase(Locale.ROOT), profile);
    }
}
