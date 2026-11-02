package cl.oxman.oxmangameoptimizer.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class PersistentHardwareSensorBridge implements LibreHardwareMonitorCpuTemperatureProvider.HelperBridge {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentHardwareSensorBridge.class);

    private final Path executable;
    private final ExecutorService responseReader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hardware-sensor-response");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean stderrLogged = new AtomicBoolean();
    private Process process;
    private BufferedReader stdout;
    private BufferedWriter stdin;
    private boolean closed;

    PersistentHardwareSensorBridge(Path executable) {
        this.executable = executable.toAbsolutePath().normalize();
    }

    @Override
    public synchronized String request(Duration timeout) throws IOException, TimeoutException {
        if (closed) throw new IOException("Hardware sensor bridge is closed");
        ensureStarted();

        Future<String> response = responseReader.submit(stdout::readLine);
        try {
            stdin.write("read");
            stdin.newLine();
            stdin.flush();
            String line = response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new IOException("Hardware sensor helper exited without a response");
            }
            return line;
        } catch (InterruptedException exception) {
            response.cancel(true);
            stopProcess();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading hardware sensor helper", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            throw cause instanceof IOException ioException
                    ? ioException
                    : new IOException("Could not read hardware sensor helper response", cause);
        } catch (TimeoutException exception) {
            response.cancel(true);
            stopProcess();
            throw exception;
        } catch (IOException exception) {
            response.cancel(true);
            stopProcess();
            throw exception;
        }
    }

    private void ensureStarted() throws IOException {
        if (process != null && process.isAlive()) return;
        stopProcess();
        if (!Files.isRegularFile(executable)) {
            throw new IOException("Hardware sensor helper not found: " + executable);
        }

        ProcessBuilder builder = new ProcessBuilder(executable.toString());
        Path workingDirectory = executable.getParent();
        if (workingDirectory != null) builder.directory(workingDirectory.toFile());
        process = builder.start();
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader stderr = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        Thread.ofPlatform().daemon().name("hardware-sensor-stderr").start(() -> drainStderr(stderr));
    }

    private void drainStderr(BufferedReader stderr) {
        try (stderr) {
            String line;
            while ((line = stderr.readLine()) != null) {
                if (!line.isBlank() && stderrLogged.compareAndSet(false, true)) {
                    LOG.warn("Hardware sensor helper: {}", line);
                }
            }
        } catch (IOException exception) {
            if (!closed && stderrLogged.compareAndSet(false, true)) {
                LOG.warn("Could not read hardware sensor helper diagnostics: {}", exception.getMessage());
            }
        }
    }

    private void stopProcess() {
        closeQuietly(stdin);
        closeQuietly(stdout);
        stdin = null;
        stdout = null;
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Process cleanup is best effort.
        }
    }

    @Override
    public synchronized void restart() throws IOException {
        if (closed) throw new IOException("Hardware sensor bridge is closed");
        stopProcess();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (process != null && process.isAlive() && stdin != null) {
            try {
                stdin.write("exit");
                stdin.newLine();
                stdin.flush();
            } catch (IOException ignored) {
                // The process may already be shutting down.
            }
        }
        stopProcess();
        responseReader.shutdownNow();
    }
}
