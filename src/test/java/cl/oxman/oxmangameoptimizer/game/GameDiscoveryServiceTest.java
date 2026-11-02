package cl.oxman.oxmangameoptimizer.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDiscoveryServiceTest {
    @TempDir Path temporary;

    @Test void detectsAProfileWhoseVerifiedExecutableIsRunning() {
        String command = ProcessHandle.current().info().command().orElseThrow();
        Path executablePath = Path.of(command).toAbsolutePath().normalize();
        String executable = executablePath.getFileName().toString();
        GameProfile profile = new GameProfile("test:running", "Running test process",
                Set.of(executable), executablePath.getParent(), null);

        assertEquals(profile, new GameDiscoveryService().findRunningGame(List.of(profile)).orElseThrow());
    }

    @Test void cs2MatchesOnlyNormalizedExecutableInsideDiscoveredInstallation() {
        Path install = temporary.resolve("steamapps/common/Counter-Strike Global Offensive");
        GameProfile profile = GameProfile.counterStrike2(install);

        assertTrue(profile.matchesExecutable(install.resolve("game/../game/bin/win64/cs2.exe")));
        assertFalse(profile.matchesExecutable(temporary.resolve("fake/cs2.exe")));
        assertFalse(profile.matchesExecutable(install.resolve("game/bin/win64/not-cs2.exe")));
    }

    @Test void cs2WithoutVerifiedInstallPathNeverMatches() {
        assertFalse(GameProfile.COUNTER_STRIKE_2.matchesExecutable(temporary.resolve("cs2.exe")));
    }

    @Test void ambiguousMatchingProcessesAreRejected() {
        ProcessHandle current = ProcessHandle.current();
        Path executable = Path.of(current.info().command().orElseThrow()).toAbsolutePath().normalize();
        GameProfile profile = new GameProfile("test:ambiguous", "Ambiguous",
                Set.of(executable.getFileName().toString()), executable.getParent(), null);

        assertTrue(profile.selectUniqueRunningProcess(List.of(current, current)).isEmpty());
    }

    @Test void steamApp730PreservesManifestInstallationDirectory() throws Exception {
        Path common = temporary.resolve("steamapps/common");
        Path install = common.resolve("Counter-Strike Global Offensive");
        Files.createDirectories(install);
        Path manifest = temporary.resolve("appmanifest_730.acf");
        Files.writeString(manifest, "\"appid\" \"730\"\n\"name\" \"Counter-Strike 2\"\n"
                + "\"installdir\" \"Counter-Strike Global Offensive\"\n");

        GameProfile profile = new GameDiscoveryService().parseSteam(manifest, common).orElseThrow();

        assertTrue(profile.matchesExecutable(install.resolve("game/bin/win64/cs2.exe")));
        assertFalse(profile.matchesExecutable(common.resolve("Other Game/cs2.exe")));
    }

    @Test void leavesPriorityPendingWhenConfiguredGameIsNotRunning() {
        GameProfile profile = new GameProfile("test:missing", "Missing game",
                Set.of("oxman-game-that-cannot-exist-7f8c3c.exe"), null, null);

        assertTrue(new GameDiscoveryService().findRunningGame(List.of(profile)).isEmpty());
        assertTrue(profile.findRunningProcessIdentity().isEmpty());
    }
}
