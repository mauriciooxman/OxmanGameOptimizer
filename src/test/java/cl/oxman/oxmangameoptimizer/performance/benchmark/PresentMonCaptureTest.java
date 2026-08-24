package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.ManagedProcess;
import cl.oxman.oxmangameoptimizer.system.CommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.io.Reader;

import static org.junit.jupiter.api.Assertions.*;

class PresentMonCaptureTest {
    @TempDir Path temporary;

    @Test void launchesWithExactProcessDurationAndANewOutputPath() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        List<String> launched = new ArrayList<>();
        PresentMonCapture capture = new PresentMonCapture(executable, temporary.resolve("benchmarks"), (command, workingDirectory) -> {
            launched.addAll(command);
            Path output = Path.of(command.get(command.indexOf("--output_file") + 1));
            assertFalse(Files.exists(output), "PresentMon output path must not exist before launch");
            Files.writeString(output, "Application,MsBetweenAppStart,MsCPUBusy,MsGPUTime\ncs2.exe,10,4,3\n");
            return completedProcess();
        }, new PresentMonCsvParser(), (command, timeout) -> new CommandResult(0, "", "", false));

        capture.start("cs2.exe", Duration.ofSeconds(30));
        GamePerformanceResult result = capture.stop();

        assertEquals("cs2.exe", launched.get(launched.indexOf("--process_name") + 1));
        assertEquals("30", launched.get(launched.indexOf("--timed") + 1));
        assertEquals(1, result.frameCount());
        assertEquals("cs2.exe", result.processName());
        assertEquals(Duration.ofSeconds(30), result.captureDuration());
    }

    @Test void nonZeroExitPreservesDiagnosticsAndDoesNotParseMissingCsv() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        PresentMonCapture capture = capture(executable, process(true, 5, "out", "access denied"));
        capture.start("cs2.exe", Duration.ofSeconds(60));
        PresentMonCaptureException error = assertThrows(PresentMonCaptureException.class, capture::stop);
        assertEquals(5, error.exitCode()); assertEquals("out", error.stdout()); assertEquals("access denied", error.stderr());
        assertEquals("PresentMon exited with code 5.", error.getMessage());
    }

    @Test void reportsMissingAndEmptyOutputSeparately() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        PresentMonCapture missing = capture(executable, process(true, 0, "", ""));
        missing.start("cs2.exe", Duration.ofSeconds(60));
        assertEquals("PresentMon exited successfully but did not create output.", assertThrows(PresentMonCaptureException.class, missing::stop).getMessage());

        PresentMonCapture empty = new PresentMonCapture(executable, temporary.resolve("empty"), (command, directory) -> {
            Files.createFile(Path.of(command.get(command.indexOf("--output_file") + 1)));
            return process(true, 0, "", "");
        }, new PresentMonCsvParser(), (command, timeout) -> new CommandResult(0, "", "", false));
        empty.start("cs2.exe", Duration.ofSeconds(60));
        assertEquals("PresentMon created an empty output file.", assertThrows(PresentMonCaptureException.class, empty::stop).getMessage());
    }

    @Test void distinguishesTimeoutAndAllowsMarginBeyondSixtySeconds() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        PresentMonCapture capture = capture(executable, process(false, -1, "partial", "waiting"));
        capture.start("cs2.exe", Duration.ofSeconds(60));
        PresentMonCaptureException error = assertThrows(PresentMonCaptureException.class, capture::stop);
        assertTrue(error.timedOut()); assertNull(error.exitCode());
        assertEquals(Duration.ofSeconds(90), PresentMonCapture.effectiveTimeout(Duration.ofSeconds(60)));
    }

    @Test void consecutiveCapturesUseUniqueNamedSessionsInCommand() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        List<List<String>> launches = new ArrayList<>();
        PresentMonCapture capture = new PresentMonCapture(executable, temporary.resolve("unique"), (command, directory) -> {
            launches.add(List.copyOf(command));
            Path output = Path.of(command.get(command.indexOf("--output_file") + 1));
            Files.writeString(output, "Application,MsBetweenAppStart,MsCPUBusy,MsGPUTime\ncs2.exe,10,4,3\n");
            return completedProcess();
        }, new PresentMonCsvParser(), (command, timeout) -> new CommandResult(0, "", "", false));

        capture.start("cs2.exe", Duration.ofSeconds(30)); capture.stop();
        capture.start("cs2.exe", Duration.ofSeconds(30)); capture.stop();

        String first = option(launches.get(0), "--session_name");
        String second = option(launches.get(1), "--session_name");
        assertTrue(first.startsWith("OxmanGameOptimizer-"));
        assertNotEquals(first, second);
        assertFalse(launches.get(0).contains("--stop_existing_session"));
    }

    @Test void cancellationStopsProcessThenCleansOnlyItsOwnedSessionAndCsv() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        TrackingProcess process = new TrackingProcess();
        List<List<String>> cleanup = new ArrayList<>();
        PresentMonCapture capture = new PresentMonCapture(executable, temporary.resolve("cancel"), (command, directory) -> {
            process.output = Path.of(option(command, "--output_file"));
            Files.writeString(process.output, "partial");
            process.session = option(command, "--session_name");
            return process;
        }, new PresentMonCsvParser(), (command, timeout) -> {
            cleanup.add(List.copyOf(command));
            return new CommandResult(0, "", "", false);
        });

        capture.start("cs2.exe", Duration.ofSeconds(30));
        capture.close();

        assertTrue(process.stopRequested);
        assertEquals(PresentMonCapture.State.IDLE, capture.state());
        assertNull(capture.activeSessionName());
        assertFalse(Files.exists(process.output));
        assertEquals(List.of(executable.toString(), "--terminate_existing_session", "--session_name", process.session), cleanup.get(0));
    }

    @Test void normalProcessExitStillCleansPotentialOrphanByOwnedSession() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        List<String> launchedSession = new ArrayList<>();
        List<List<String>> cleanup = new ArrayList<>();
        PresentMonCapture capture = new PresentMonCapture(executable, temporary.resolve("orphan"), (command, directory) -> {
            launchedSession.add(option(command, "--session_name"));
            Files.writeString(Path.of(option(command, "--output_file")),
                    "Application,MsBetweenAppStart,MsCPUBusy,MsGPUTime\ncs2.exe,10,4,3\n");
            return completedProcess();
        }, new PresentMonCsvParser(), (command, timeout) -> {
            cleanup.add(List.copyOf(command));
            return new CommandResult(0, "", "", false);
        });

        capture.start("cs2.exe", Duration.ofSeconds(30)); capture.stop();

        assertEquals(launchedSession.get(0), option(cleanup.get(0), "--session_name"));
        assertFalse(cleanup.get(0).contains("PresentMon"));
    }

    @Test void exitCodeSixWithCollisionStderrProducesClearDiagnostic() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        PresentMonCapture capture = capture(executable,
                process(true, 6, "", "a trace session named PresentMon is already running"));
        capture.start("cs2.exe", Duration.ofSeconds(30));
        PresentMonCaptureException error = assertThrows(PresentMonCaptureException.class, capture::stop);
        assertEquals(6, error.exitCode());
        assertTrue(error.getMessage().contains("trace session"));
        assertTrue(error.getMessage().contains("already running"));
        assertEquals("a trace session named PresentMon is already running", error.stderr());
    }

    @Test void commandMatchesProductionOrderWithUniqueSessionAndLocalAppDataOutput() {
        Path executable = Path.of("tools", "PresentMon.exe");
        Path output = Path.of(System.getenv().getOrDefault("LOCALAPPDATA", temporary.toString()),
                "OxmanGameOptimizer", "benchmarks", "diagnostic.csv");
        String session = "OxmanGameOptimizer-Test-" + java.util.UUID.randomUUID();

        assertEquals(List.of(executable.toString(), "--process_name", "cs2.exe", "--timed", "30",
                "--terminate_after_timed", "--no_console_stats", "--output_file", output.toString(),
                "--session_name", session),
                PresentMonCapture.buildCommand(executable, "cs2.exe", Duration.ofSeconds(30), output, session));
    }

    @Test void parsesBeforeDeletingCsvAndCleansSessionAfterDeletingIt() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        List<String> events = new ArrayList<>();
        Path[] output = new Path[1];
        PresentMonCsvParser checkingParser = new PresentMonCsvParser() {
            @Override public GamePerformanceResult parse(Reader input, String processName, Duration duration) throws java.io.IOException {
                assertTrue(Files.exists(output[0]), "CSV must exist while parser runs");
                events.add("parse");
                return super.parse(input, processName, duration);
            }
        };
        PresentMonCapture capture = new PresentMonCapture(executable, temporary.resolve("order"), (command, directory) -> {
            output[0] = Path.of(option(command, "--output_file"));
            Files.writeString(output[0], "Application,MsBetweenAppStart,MsCPUBusy,MsGPUTime\ncs2.exe,10,4,3\n");
            return completedProcess();
        }, checkingParser, (command, timeout) -> {
            assertFalse(Files.exists(output[0]), "CSV must be deleted before session cleanup");
            events.add("session-cleanup");
            return new CommandResult(0, "", "", false);
        });

        capture.start("cs2.exe", Duration.ofSeconds(30));
        assertEquals(1, capture.stop().frameCount());
        assertEquals(List.of("parse", "session-cleanup"), events);
        assertFalse(Files.exists(output[0]));
    }

    @Test void visibleDiagnosticIncludesExitCodeStderrElapsedSessionAndMissingOutput() throws Exception {
        Path executable = Files.createFile(temporary.resolve("PresentMon.exe"));
        List<String> diagnostics = new ArrayList<>();
        PresentMonCapture capture = new PresentMonCapture(executable, temporary.resolve("visible"),
                (command, directory) -> process(true, 6, "tool output", "trace startup failed"),
                new PresentMonCsvParser(), (command, timeout) -> new CommandResult(0, "", "", false), diagnostics::add);

        capture.start("cs2.exe", Duration.ofSeconds(30));
        assertThrows(PresentMonCaptureException.class, capture::stop);
        String visible = String.join("\n", diagnostics);
        assertTrue(visible.contains("PresentMon command:"));
        assertTrue(visible.contains("Session: OxmanGameOptimizer-"));
        assertTrue(visible.contains("Exit code: 6"));
        assertTrue(visible.contains("Elapsed:"));
        assertTrue(visible.contains("stderr: trace startup failed"));
        assertTrue(visible.contains("Created: NO"));
    }

    private PresentMonCapture capture(Path executable, ManagedProcess process) {
        return new PresentMonCapture(executable, temporary.resolve("benchmarks-" + System.nanoTime()),
                (command, directory) -> process, new PresentMonCsvParser(),
                (command, timeout) -> new CommandResult(0, "", "", false));
    }

    private static String option(List<String> command, String name) {
        return command.get(command.indexOf(name) + 1);
    }

    private static ManagedProcess completedProcess() {
        return process(true, 0, "", "");
    }

    private static ManagedProcess process(boolean finished, int exitCode, String stdout, String stderr) {
        return new ManagedProcess() {
            public boolean waitFor(Duration timeout) { return finished; }
            public int exitCode() { return exitCode; }
            public String stdout() { return stdout; }
            public String stderr() { return stderr; }
            public void requestStop() { }
            public void forceStop() { }
        };
    }

    private static final class TrackingProcess implements ManagedProcess {
        boolean stopRequested;
        Path output;
        String session;
        public boolean waitFor(Duration timeout) { return stopRequested; }
        public int exitCode() { return 0; }
        public String stdout() { return ""; }
        public String stderr() { return ""; }
        public void requestStop() { stopRequested = true; }
        public void forceStop() { stopRequested = true; }
    }
}
