package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.CommandResult;
import cl.oxman.oxmangameoptimizer.system.ManagedProcess;
import cl.oxman.oxmangameoptimizer.system.ManagedProcessLauncher;
import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;
import cl.oxman.oxmangameoptimizer.system.WindowsCommandRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional adapter for the official standalone PresentMon console binary (MIT licensed). */
public final class PresentMonCapture implements GamePerformanceCapture {
    private static final Logger LOG = LoggerFactory.getLogger(PresentMonCapture.class);
    private static final String SESSION_PREFIX = "OxmanGameOptimizer-";
    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(5);
    static final Duration PROCESS_MARGIN = Duration.ofSeconds(30);
    enum State { IDLE, ACTIVE, STOPPING }

    private final Path executable, benchmarkDirectory;
    private final ManagedProcessLauncher launcher;
    private final PresentMonCsvParser parser;
    private final SystemCommandRunner commands;
    private final Consumer<String> diagnosticLog;
    private ManagedProcess process;
    private Path csv;
    private String processName;
    private String sessionName;
    private Duration duration;
    private State state = State.IDLE;
    private long startedNanos;

    public PresentMonCapture(Path executable, Path benchmarkDirectory, ManagedProcessLauncher launcher,
            PresentMonCsvParser parser) {
        this(executable, benchmarkDirectory, launcher, parser, new WindowsCommandRunner(), ignored -> { });
    }

    public PresentMonCapture(Path executable, Path benchmarkDirectory, ManagedProcessLauncher launcher,
            PresentMonCsvParser parser, Consumer<String> diagnosticLog) {
        this(executable, benchmarkDirectory, launcher, parser, new WindowsCommandRunner(), diagnosticLog);
    }

    PresentMonCapture(Path executable, Path benchmarkDirectory, ManagedProcessLauncher launcher,
            PresentMonCsvParser parser, SystemCommandRunner commands) {
        this(executable, benchmarkDirectory, launcher, parser, commands, ignored -> { });
    }

    PresentMonCapture(Path executable, Path benchmarkDirectory, ManagedProcessLauncher launcher,
            PresentMonCsvParser parser, SystemCommandRunner commands, Consumer<String> diagnosticLog) {
        this.executable = executable;
        this.benchmarkDirectory = benchmarkDirectory;
        this.launcher = launcher;
        this.parser = parser;
        this.commands = commands;
        this.diagnosticLog = diagnosticLog == null ? ignored -> { } : diagnosticLog;
    }

    public static Path configuredExecutable() {
        String configured = System.getenv("OXMAN_PRESENTMON");
        if (configured != null && !configured.isBlank()) return Path.of(configured);

        String packagedLauncher = System.getProperty("jpackage.app-path");
        if (packagedLauncher != null && !packagedLauncher.isBlank()) {
            Path launcherDirectory = Path.of(packagedLauncher).toAbsolutePath().getParent();
            if (launcherDirectory != null) return launcherDirectory.resolve("tools").resolve("PresentMon.exe");
        }
        return Path.of("tools", "PresentMon.exe").toAbsolutePath();
    }

    public boolean isAvailable() { return Files.isRegularFile(executable); }

    public synchronized void start(String targetProcess, Duration captureDuration) throws IOException {
        if (state != State.IDLE) throw new IllegalStateException("PresentMon capture already active");
        if (!isAvailable()) throw new IOException("PresentMon executable not found");
        if (targetProcess == null || !targetProcess.toLowerCase(Locale.ROOT).endsWith(".exe"))
            throw new IllegalArgumentException("Unsafe process name");
        Files.createDirectories(benchmarkDirectory);
        if (!Files.isDirectory(benchmarkDirectory)) throw new IOException("PresentMon output parent is not a directory");
        verifyWritableDirectory();
        Path output;
        do { output = benchmarkDirectory.resolve("presentmon-" + UUID.randomUUID() + ".csv"); }
        while (Files.exists(output));
        String ownedSession = SESSION_PREFIX + UUID.randomUUID();
        csv = output;
        sessionName = ownedSession;
        processName = targetProcess;
        duration = captureDuration;
        state = State.ACTIVE;
        startedNanos = System.nanoTime();
        List<String> command = buildCommand(targetProcess, captureDuration, output, ownedSession);
        LOG.info("PresentMon executable: {}", executable);
        LOG.info("PresentMon session: {}", ownedSession);
        LOG.info("Target: {}", targetProcess);
        LOG.info("Duration: {} seconds", captureDuration.toSeconds());
        LOG.info("Effective timeout: {} seconds", effectiveTimeout(captureDuration).toSeconds());
        LOG.info("Output: {}", output);
        logPreflight(command, output, ownedSession);
        try {
            process = launcher.start(command, executable.getParent());
            LOG.info("PresentMon started: YES");
        } catch (IOException | RuntimeException error) {
            state = State.STOPPING;
            cleanupSession(ownedSession);
            clearCapture(ownedSession, null);
            Files.deleteIfExists(output);
            throw error;
        }
    }

    public GamePerformanceResult stop() throws Exception {
        ManagedProcess activeProcess;
        Path activeCsv;
        String activeName;
        String activeSession;
        Duration activeDuration;
        synchronized (this) {
            if (state == State.IDLE || process == null) throw new IllegalStateException("No PresentMon capture active");
            activeProcess = process;
            activeCsv = csv;
            activeName = processName;
            activeSession = sessionName;
            activeDuration = duration;
        }
        try {
            Duration timeout = effectiveTimeout(activeDuration);
            if (!activeProcess.waitFor(timeout)) {
                stopProcess(activeProcess);
                throw failure("PresentMon capture timed out after " + timeout.toSeconds() + " seconds.", null,
                        activeProcess, activeCsv, activeSession, true);
            }
            int exitCode = activeProcess.exitCode();
            String stdout = activeProcess.stdout();
            String stderr = activeProcess.stderr();
            logResult(exitCode, stdout, stderr, activeCsv, activeSession);
            if (exitCode != 0) throw exitFailure(exitCode, stdout, stderr);
            if (!Files.isRegularFile(activeCsv))
                throw new PresentMonCaptureException("PresentMon exited successfully but did not create output.", exitCode, stdout, stderr, false);
            if (Files.size(activeCsv) == 0)
                throw new PresentMonCaptureException("PresentMon created an empty output file.", exitCode, stdout, stderr, false);
            try (var reader = Files.newBufferedReader(activeCsv, StandardCharsets.UTF_8)) {
                return parser.parse(reader, activeName, activeDuration);
            }
        } finally {
            synchronized (this) { if (activeSession.equals(sessionName)) state = State.STOPPING; }
            try { Files.deleteIfExists(activeCsv); }
            finally {
                cleanupSession(activeSession);
                synchronized (this) { clearCapture(activeSession, activeProcess); }
            }
        }
    }

    static List<String> buildCommand(Path executable, String targetProcess, Duration captureDuration,
            Path output, String ownedSession) {
        return List.of(executable.toString(), "--process_name", targetProcess,
                "--timed", Long.toString(captureDuration.toSeconds()), "--terminate_after_timed",
                "--no_console_stats", "--output_file", output.toString(), "--session_name", ownedSession);
    }

    private List<String> buildCommand(String targetProcess, Duration captureDuration, Path output, String ownedSession) {
        return buildCommand(executable, targetProcess, captureDuration, output, ownedSession);
    }

    private void logPreflight(List<String> command, Path output, String ownedSession) {
        String rendered = command.stream().map(PresentMonCapture::quoteArgument).reduce((a, b) -> a + " " + b).orElse("");
        LOG.info("PresentMon command: {}", rendered);
        LOG.info("Output parent exists before launch: {}", Files.isDirectory(output.getParent()) ? "YES" : "NO");
        LOG.info("Output exists before launch: {}", Files.exists(output) ? "YES" : "NO");
        diagnosticLog.accept("PresentMon command: " + rendered);
        diagnosticLog.accept("PresentMon preflight · Session: " + ownedSession + " · Parent exists: "
                + yesNo(Files.isDirectory(output.getParent())) + " · Output exists before launch: " + yesNo(Files.exists(output)));
    }

    private static String quoteArgument(String value) {
        return value.chars().anyMatch(Character::isWhitespace) ? '"' + value.replace("\"", "\\\"") + '"' : value;
    }

    private static String yesNo(boolean value) { return value ? "YES" : "NO"; }

    static Duration effectiveTimeout(Duration captureDuration) { return captureDuration.plus(PROCESS_MARGIN); }
    synchronized String activeSessionName() { return sessionName; }
    synchronized State state() { return state; }

    private void verifyWritableDirectory() throws IOException {
        Path probe = Files.createTempFile(benchmarkDirectory, ".oxman-write-probe-", ".tmp");
        Files.delete(probe);
    }

    private PresentMonCaptureException exitFailure(int exitCode, String stdout, String stderr) {
        String normalized = stderr.toLowerCase(Locale.ROOT);
        if (exitCode == 6 && (normalized.contains("already running") || normalized.contains("already exists")))
            return new PresentMonCaptureException("PresentMon could not start its trace session (exit code 6): "
                    + "a trace session is already running.", exitCode, stdout, stderr, false);
        if (exitCode == 6 && normalized.contains("failed to start trace session"))
            return new PresentMonCaptureException("PresentMon could not start its trace session (exit code 6).",
                    exitCode, stdout, stderr, false);
        return new PresentMonCaptureException("PresentMon exited with code " + exitCode + ".",
                exitCode, stdout, stderr, false);
    }

    private PresentMonCaptureException failure(String message, Integer exitCode, ManagedProcess active, Path output,
            String ownedSession, boolean timedOut) throws InterruptedException {
        String stdout = active.stdout();
        String stderr = active.stderr();
        logResult(exitCode, stdout, stderr, output, ownedSession);
        return new PresentMonCaptureException(message, exitCode, stdout, stderr, timedOut);
    }

    private void logResult(Integer exitCode, String stdout, String stderr, Path output, String ownedSession) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000;
        LOG.info("PresentMon session: {}", ownedSession);
        LOG.info("PresentMon terminated after {} ms", elapsedMs);
        LOG.info("PresentMon exit code: {}", exitCode == null ? "N/A (timeout)" : exitCode);
        LOG.info("PresentMon stdout: {}", stdout.isBlank() ? "<empty>" : stdout.strip());
        LOG.info("PresentMon stderr: {}", stderr.isBlank() ? "<empty>" : stderr.strip());
        LOG.info("Output file created: {}", Files.isRegularFile(output) ? "YES" : "NO");
        String size = "N/A";
        try { size = Files.isRegularFile(output) ? Long.toString(Files.size(output)) : "N/A"; }
        catch (IOException error) { LOG.info("Output file size: unavailable ({})", error.getMessage()); }
        LOG.info("Output file size: {}", size);
        diagnosticLog.accept("PresentMon " + (exitCode != null && exitCode == 0 && Files.isRegularFile(output) ? "completed" : "failed")
                + " · Session: " + ownedSession + " · Exit code: " + (exitCode == null ? "N/A (timeout)" : exitCode)
                + " · Elapsed: " + String.format(Locale.ROOT, "%.3fs", elapsedMs / 1000.0));
        diagnosticLog.accept("stdout: " + compact(stdout) + " · stderr: " + compact(stderr));
        diagnosticLog.accept("Output: " + output + " · Created: " + yesNo(Files.isRegularFile(output)) + " · Size: " + size);
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) return "<empty>";
        String singleLine = value.strip().replaceAll("\\R+", " | ");
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500) + "…";
    }

    private void stopProcess(ManagedProcess active) throws InterruptedException {
        active.requestStop();
        if (!active.waitFor(Duration.ofSeconds(3))) {
            active.forceStop();
            active.waitFor(Duration.ofSeconds(3));
        }
    }

    private void cleanupSession(String ownedSession) {
        if (ownedSession == null || !ownedSession.startsWith(SESSION_PREFIX)) return;
        CommandResult result = commands.run(List.of(executable.toString(), "--terminate_existing_session",
                "--session_name", ownedSession), CLEANUP_TIMEOUT);
        LOG.info("PresentMon session cleanup: session={}, exitCode={}, timedOut={}, stderr={}", ownedSession,
                result.exitCode(), result.timedOut(), result.stderr().isBlank() ? "<empty>" : result.stderr().strip());
    }

    private synchronized void clearCapture(String ownedSession, ManagedProcess activeProcess) {
        if (sessionName == null || !sessionName.equals(ownedSession)) return;
        if (activeProcess != null && process != activeProcess) return;
        process = null;
        csv = null;
        processName = null;
        sessionName = null;
        duration = null;
        state = State.IDLE;
    }

    public void close() {
        ManagedProcess activeProcess;
        Path activeCsv;
        String activeSession;
        synchronized (this) {
            if (state == State.IDLE) return;
            state = State.STOPPING;
            activeProcess = process;
            activeCsv = csv;
            activeSession = sessionName;
        }
        if (activeProcess != null) {
            try { stopProcess(activeProcess); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                activeProcess.forceStop();
            }
        }
        cleanupSession(activeSession);
        synchronized (this) { clearCapture(activeSession, activeProcess); }
        if (activeCsv != null) try { Files.deleteIfExists(activeCsv); }
        catch (IOException error) { LOG.warn("Could not delete PresentMon output {}", activeCsv, error); }
    }
}
