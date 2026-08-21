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
        discoverRiot().forEach(game -> add(games, game));
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

    private List<GameProfile> discoverRiot() {
        Map<String, Path> installs = new LinkedHashMap<>();
        Path programData = Path.of(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"));
        Path riotData = programData.resolve("Riot Games");

        // Riot's product metadata is the most reliable source and also supports custom drives.
        Path metadata = riotData.resolve("Metadata");
        if (Files.isDirectory(metadata)) try (var files = Files.walk(metadata, 4)) {
            files.filter(path -> path.getFileName().toString().endsWith(".product_settings.yaml"))
                    .forEach(path -> riotInstallFromSettings(path).ifPresent(install -> {
                        String file = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (file.contains("valorant")) installs.putIfAbsent("valorant", install);
                        else if (file.contains("league_of_legends") || file.contains("league-of-legends"))
                            installs.putIfAbsent("league_of_legends", install);
                    }));
        } catch (IOException | RuntimeException ignored) { }

        // RiotClientInstalls.json tells us the Riot root even when it is outside C:.
        Path clientInstalls = riotData.resolve("RiotClientInstalls.json");
        if (Files.isRegularFile(clientInstalls)) try {
            var matcher = JSON.matcher(Files.readString(clientInstalls));
            while (matcher.find()) {
                String value = unescape(matcher.group(2));
                if (!value.toLowerCase(Locale.ROOT).endsWith("riotclientservices.exe")) continue;
                Path client = Path.of(value.replace('/', '\\')).toAbsolutePath().normalize();
                Path riotRoot = client.getParent() == null ? null : client.getParent().getParent();
                if (riotRoot != null) addRiotRootCandidates(installs, riotRoot);
            }
        } catch (IOException | RuntimeException ignored) { }

        addRiotRootCandidates(installs, Path.of("C:\\Riot Games"));
        for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)")) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) addRiotRootCandidates(installs, Path.of(value, "Riot Games"));
        }

        List<GameProfile> result = new ArrayList<>();
        Path valorant = installs.get("valorant");
        if (valorant != null) result.add(new GameProfile("riot:valorant", "VALORANT",
                java.util.Set.of("valorant-win64-shipping.exe", "valorant.exe"), valorant,
                "riotclient://launch-product=valorant&launch-patchline=live"));
        Path league = installs.get("league_of_legends");
        if (league != null) result.add(new GameProfile("riot:league_of_legends", "League of Legends",
                java.util.Set.of("league of legends.exe", "leagueclient.exe", "leagueclientux.exe"), league,
                "riotclient://launch-product=league_of_legends&launch-patchline=live"));
        return result;
    }

    private static Optional<Path> riotInstallFromSettings(Path settings) {
        try {
            for (String line : Files.readAllLines(settings, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                String key = "product_install_full_path:";
                if (!trimmed.startsWith(key)) continue;
                String value = trimmed.substring(key.length()).trim();
                int comment = value.indexOf(" #");
                if (comment >= 0) value = value.substring(0, comment).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                Path install = Path.of(value.replace('/', '\\')).toAbsolutePath().normalize();
                return Files.isDirectory(install) ? Optional.of(install) : Optional.empty();
            }
        } catch (IOException | RuntimeException ignored) { }
        return Optional.empty();
    }

    private static void addRiotRootCandidates(Map<String, Path> installs, Path root) {
        Path valorant = root.resolve("VALORANT");
        if (Files.isDirectory(valorant)) installs.putIfAbsent("valorant", valorant);
        Path league = root.resolve("League of Legends");
        if (Files.isDirectory(league)) installs.putIfAbsent("league_of_legends", league);
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
