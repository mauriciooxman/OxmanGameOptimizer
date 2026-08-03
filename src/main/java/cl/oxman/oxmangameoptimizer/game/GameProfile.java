package cl.oxman.oxmangameoptimizer.game;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public enum GameProfile {
    COUNTER_STRIKE_2(
            "Counter-Strike 2",
            "/cl/oxman/oxmangameoptimizer/cs2-icon.png",
            Set.of("cs2.exe"),
            List.of()
    ),
    VALORANT(
            "VALORANT",
            "/cl/oxman/oxmangameoptimizer/valorant-icon.png",
            Set.of("valorant-win64-shipping.exe", "valorant.exe"),
            List.of(
                    Path.of("C:\\Riot Games\\Riot Client\\RiotClientServices.exe"),
                    Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"),
                            "Riot Games", "Riot Client", "RiotClientServices.exe")
            )
    );

    private final String displayName;
    private final String iconResource;
    private final Set<String> processNames;
    private final List<Path> launcherCandidates;

    GameProfile(String displayName, String iconResource,
                Set<String> processNames, List<Path> launcherCandidates) {
        this.displayName = displayName;
        this.iconResource = iconResource;
        this.processNames = processNames;
        this.launcherCandidates = launcherCandidates;
    }

    public boolean launch() {
        try {
            if (this == COUNTER_STRIKE_2) {
                new ProcessBuilder("cmd", "/c", "start", "", "steam://rungameid/730").start();
                return true;
            }

            for (Path launcher : launcherCandidates) {
                if (Files.isRegularFile(launcher)) {
                    String command = "start \"\" \"" + launcher
                            + "\" --launch-product=valorant --launch-patchline=live";
                    int exitCode = new ProcessBuilder("cmd", "/c", command)
                            .redirectErrorStream(true)
                            .start()
                            .waitFor();
                    if (exitCode == 0) {
                        LogManager.addLog("✔ Riot Client iniciado desde: " + launcher);
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            LogManager.addLog("❌ No se pudo abrir el launcher del juego.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LogManager.addLog("❌ El inicio del juego fue interrumpido.");
        }

        return false;
    }

    public void retryLaunch() {
        if (this != VALORANT) {
            return;
        }

        try {
            String command = "start \"\" "
                    + "\"riotclient://launch-product=valorant&launch-patchline=live\"";
            new ProcessBuilder("cmd", "/c", command)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            LogManager.addLog("↻ Reintentando VALORANT mediante el protocolo de Riot...");
        } catch (IOException e) {
            LogManager.addLog("⚠ No se pudo reenviar la orden a Riot Client.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() {
        for (String processName : processNames) {
            try {
                Process process = new ProcessBuilder(
                        "tasklist", "/FI", "IMAGENAME eq " + processName, "/NH")
                        .redirectErrorStream(true)
                        .start();
                String output = new String(
                        process.getInputStream().readAllBytes(), Charset.defaultCharset());
                if (process.waitFor() == 0
                        && output.toLowerCase().contains(processName)) {
                    return true;
                }
            } catch (IOException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public String getIconResource() {
        return iconResource;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
