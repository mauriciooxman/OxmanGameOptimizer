package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.*;

/** Allowlist-first selection. Unknown, system, audio, launcher and anti-cheat processes stay excluded. */
public final class BackgroundProcessSelector {
    public static final int MAXIMUM_PROCESSES = 3;
    static final double MINIMUM_CPU_PERCENT = 0.5;
    private static final Set<String> ALLOWED = Set.of(
            "chrome.exe", "msedge.exe", "firefox.exe", "onedrive.exe", "dropbox.exe");
    private static final Set<String> EXCLUDED = Set.of(
            "system", "registry", "csrss.exe", "wininit.exe", "winlogon.exe", "services.exe", "lsass.exe", "smss.exe",
            "dwm.exe", "explorer.exe", "audiodg.exe", "presentmon.exe", "steam.exe", "steamwebhelper.exe",
            "discord.exe", "spotify.exe", "msmpeng.exe", "nissrv.exe", "securityhealthservice.exe",
            "nvcontainer.exe", "nvidia share.exe", "amdrsserv.exe", "radeonsoftware.exe", "igfxem.exe",
            "vgc.exe", "vgtray.exe", "easyanticheat.exe", "easyanticheat_eos.exe", "battleye.exe",
            "gameinputsvc.exe", "ctfmon.exe");
    private static final List<String> EXCLUDED_FRAGMENTS = List.of(
            "anticheat", "anti-cheat", "defender", "audio", "nahimic", "realtek", "steelseries", "razer",
            "corsair", "logitech", "nvidia", "radeon", "amd", "intel", "launcher", "oxmangameoptimizer");

    public List<BackgroundProcessObservation> select(List<BackgroundProcessObservation> observations,
            long gamePid, long oxmanPid) {
        return observations.stream().filter(value -> eligible(value, gamePid, oxmanPid))
                .sorted(Comparator.comparingDouble(BackgroundProcessObservation::cpuAveragePercent).reversed())
                .limit(MAXIMUM_PROCESSES).toList();
    }

    private static boolean eligible(BackgroundProcessObservation value, long gamePid, long oxmanPid) {
        if (value == null || value.identity() == null || !value.currentUser() || value.foreground()) return false;
        if (value.identity().pid() == gamePid || value.identity().pid() == oxmanPid) return false;
        if (!Double.isFinite(value.cpuAveragePercent()) || value.cpuAveragePercent() < MINIMUM_CPU_PERCENT) return false;
        String name = value.identity().processName().toLowerCase(Locale.ROOT);
        if (EXCLUDED.contains(name) || EXCLUDED_FRAGMENTS.stream().anyMatch(name::contains)) return false;
        return ALLOWED.contains(name);
    }
}
