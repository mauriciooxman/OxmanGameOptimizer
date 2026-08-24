package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.CommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PresentMonDiagnosticTest {
    @TempDir Path temporary;

    @Test void readsOfficialVersionEvenWhenHelpReturnsExitCodeOne() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        PresentMonDiagnostic diagnostic = PresentMonDiagnostic.inspect(executable,
                (command, timeout) -> new CommandResult(1, "PresentMon 2.5.1\nCapture Target Options:", false));
        assertEquals(PresentMonDiagnostic.Status.AVAILABLE, diagnostic.status());
        assertEquals("2.5.1", diagnostic.version());
    }
}
