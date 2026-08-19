package cl.oxman.oxmangameoptimizer.game;

import cl.oxman.oxmangameoptimizer.ui.LogManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class GameProfile {
    public static final GameProfile COUNTER_STRIKE_2 = new GameProfile("steam:730", "Counter-Strike 2",
            Set.of("cs2.exe"), null, "steam://rungameid/730");
    public static final GameProfile VALORANT = new GameProfile("riot:valorant", "VALORANT",
            Set.of("valorant-win64-shipping.exe", "valorant.exe"), Path.of("C:\\Riot Games\\VALORANT"),
            "riotclient://launch-product=valorant&launch-patchline=live");

    private final String id;
    private final String displayName;
    private final Set<String> processNames;
    private final Path installDirectory;
    private final String launchUri;

    public GameProfile(String id, String displayName, Set<String> processNames,
                       Path installDirectory, String launchUri) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.processNames = processNames.stream().map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.installDirectory = installDirectory == null ? null : installDirectory.toAbsolutePath().normalize();
        this.launchUri = launchUri;
    }

    public static GameProfile installed(String id, String name, Path directory, String launchUri) {
        return new GameProfile(id, name, Set.of(), directory, launchUri);
    }

    public boolean launch() {
        if (launchUri == null || launchUri.isBlank()) return false;
        try {
            new ProcessBuilder("cmd", "/c", "start", "", launchUri).start();
            return true;
        } catch (IOException exception) {
            LogManager.addLog("❌ No se pudo abrir el launcher de " + displayName + ".");
            return false;
        }
    }

    public void retryLaunch() { if (equals(VALORANT)) launch(); }

    public boolean matches(ProcessHandle process) {
        var command = process.info().command();
        if (command.isEmpty()) return false;
        try {
            Path executable = Path.of(command.get()).toAbsolutePath().normalize();
            String fileName = executable.getFileName().toString().toLowerCase(Locale.ROOT);
            if (processNames.contains(fileName)) return true;
            return installDirectory != null && executable.startsWith(installDirectory)
                    && !isLauncherOrHelper(fileName);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean isRunning() { return ProcessHandle.allProcesses().anyMatch(this::matches); }

    private static boolean isLauncherOrHelper(String name) {
        return name.contains("launcher") || name.contains("crash") || name.contains("reporter")
                || name.contains("helper") || name.contains("service") || name.contains("unins");
    }

    public String getId() { return id; }
    @Override public String toString() { return displayName; }
    @Override public boolean equals(Object other) { return other instanceof GameProfile p && id.equals(p.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
