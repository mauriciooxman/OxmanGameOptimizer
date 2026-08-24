package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundProcessSelectorTest {
    private final BackgroundProcessSelector selector = new BackgroundProcessSelector();

    @Test void selectsOnlyCurrentUserBackgroundAllowlistedProcessesByCpuAndLimitsToThree() {
        List<BackgroundProcessObservation> selected = selector.select(List.of(
                observation(10, "chrome.exe", 2, true, false), observation(11, "msedge.exe", 8, true, false),
                observation(12, "firefox.exe", 5, true, false), observation(13, "OneDrive.exe", 4, true, false),
                observation(14, "unknown.exe", 99, true, false), observation(15, "chrome.exe", 30, false, false),
                observation(16, "chrome.exe", 30, true, true)), 90, 91);
        assertEquals(List.of(11L, 12L, 13L), selected.stream().map(v -> v.identity().pid()).toList());
    }

    @Test void neverSelectsGameOxmanSteamAnticheatAudioOrSystemProcesses() {
        List<BackgroundProcessObservation> values = new ArrayList<>();
        values.add(observation(42, "chrome.exe", 10, true, false));
        values.add(observation(43, "chrome.exe", 10, true, false));
        for (String name : List.of("steam.exe", "steamwebhelper.exe", "Discord.exe", "Spotify.exe", "audiodg.exe",
                "csrss.exe", "MsMpEng.exe", "EasyAntiCheat.exe", "nvcontainer.exe", "OxmanGameOptimizer.exe"))
            values.add(observation(values.size() + 100, name, 50, true, false));
        assertTrue(selector.select(values, 42, 43).isEmpty());
    }

    @Test void ignoresIdleAndForegroundCandidates() {
        assertTrue(selector.select(List.of(observation(1, "chrome.exe", 0.1, true, false),
                observation(2, "OneDrive.exe", 4, true, true)), 9, 10).isEmpty());
    }

    private static BackgroundProcessObservation observation(long pid, String name, double cpu,
            boolean currentUser, boolean foreground) {
        return new BackgroundProcessObservation(new ProcessIdentity(pid, pid * 100, name), "user", currentUser, foreground, cpu);
    }
}
