package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public record PresentMonDiagnostic(Status status, String version, CaptureTest captureTest) {
    public enum Status { AVAILABLE, NOT_FOUND }
    public enum CaptureTest { PASSED, FAILED, NOT_RUN }
    public static PresentMonDiagnostic inspect(Path executable, SystemCommandRunner commands) {
        if (!Files.isRegularFile(executable)) return new PresentMonDiagnostic(Status.NOT_FOUND, "N/A", CaptureTest.NOT_RUN);
        var result = commands.run(List.of(executable.toString(), "--help"), Duration.ofSeconds(5));
        String version = "Unknown";
        // The official 2.5.1 console prints valid help/version output but returns exit code 1 for --help.
        var matcher = Pattern.compile("PresentMon\\s+([0-9][^\\s]*)", Pattern.CASE_INSENSITIVE).matcher(result.output());
        if (!result.timedOut() && matcher.find()) version = matcher.group(1);
        return new PresentMonDiagnostic(Status.AVAILABLE, version, CaptureTest.NOT_RUN);
    }
}
