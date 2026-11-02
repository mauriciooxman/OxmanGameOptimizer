package cl.oxman.oxmangameoptimizer.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LibreHardwareMonitorCpuTemperatureProvider implements CpuTemperatureProvider {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    static final Duration FAILURE_RETRY_DELAY = Duration.ofSeconds(30);

    private static final Pattern AVAILABLE = Pattern.compile("\\\"available\\\"\\s*:\\s*(true|false)");
    private static final Pattern TEMPERATURE = Pattern.compile(
            "\\\"temperatureCelsius\\\"\\s*:\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)");

    private final HelperBridge bridge;
    private final LongSupplier nanoTime;
    private long retryAfterNanos;
    private String retryReason = "HELPER_UNAVAILABLE";

    LibreHardwareMonitorCpuTemperatureProvider(Path executable) {
        this(new PersistentHardwareSensorBridge(executable), System::nanoTime);
    }

    LibreHardwareMonitorCpuTemperatureProvider(HelperBridge bridge) {
        this(bridge, System::nanoTime);
    }

    LibreHardwareMonitorCpuTemperatureProvider(HelperBridge bridge, LongSupplier nanoTime) {
        this.bridge = bridge;
        this.nanoTime = nanoTime;
    }

    static Path configuredExecutable() {
        String configured = System.getenv("OXMAN_HARDWARE_SENSOR");
        if (configured != null && !configured.isBlank()) return Path.of(configured);

        String packagedLauncher = System.getProperty("jpackage.app-path");
        if (packagedLauncher != null && !packagedLauncher.isBlank()) {
            Path launcherDirectory = Path.of(packagedLauncher).toAbsolutePath().getParent();
            if (launcherDirectory != null) {
                return launcherDirectory.resolve("tools").resolve("hardware")
                        .resolve("OxmanHardwareSensor.exe");
            }
        }

        Path published = Path.of("target", "hardware-helper", "OxmanHardwareSensor.exe").toAbsolutePath();
        if (Files.isRegularFile(published)) return published;
        return Path.of("tools", "hardware", "OxmanHardwareSensor.exe").toAbsolutePath();
    }

    @Override
    public synchronized CpuTemperatureReading read() {
        long now = nanoTime.getAsLong();
        if (now < retryAfterNanos) {
            return CpuTemperatureReading.unavailable("LibreHardwareMonitor", retryReason);
        }

        try {
            CpuTemperatureReading result = parse(bridge.request(REQUEST_TIMEOUT));
            if (result.available()) {
                retryAfterNanos = 0L;
                return result;
            }
            delayRetry(now, result.reason());
            return result;
        } catch (TimeoutException exception) {
            return failure(now, "TIMEOUT");
        } catch (IOException exception) {
            return failure(now, "PROCESS_FAILED");
        } catch (IllegalArgumentException exception) {
            restartBridge();
            return failure(now, "INVALID_JSON");
        }
    }

    private CpuTemperatureReading failure(long now, String reason) {
        delayRetry(now, reason);
        return CpuTemperatureReading.unavailable("LibreHardwareMonitor", reason);
    }

    private void delayRetry(long now, String reason) {
        retryReason = reason;
        retryAfterNanos = now + FAILURE_RETRY_DELAY.toNanos();
    }

    static CpuTemperatureReading parse(String json) {
        if (json == null || !json.strip().startsWith("{") || !json.strip().endsWith("}")) {
            throw new IllegalArgumentException("Expected a JSON object");
        }

        Matcher available = AVAILABLE.matcher(json);
        String provider = stringValue(json, "provider")
                .orElseThrow(() -> new IllegalArgumentException("Missing provider"));
        if (!available.find() || !provider.equals("LibreHardwareMonitor")) {
            throw new IllegalArgumentException("Invalid helper response");
        }

        if (!Boolean.parseBoolean(available.group(1))) {
            return CpuTemperatureReading.unavailable(provider,
                    stringValue(json, "reason").orElse("HELPER_UNAVAILABLE"));
        }

        Matcher temperature = TEMPERATURE.matcher(json);
        if (!temperature.find()) throw new IllegalArgumentException("Missing temperatureCelsius");
        double value = Double.parseDouble(temperature.group(1));
        String sensor = stringValue(json, "sensor")
                .orElseThrow(() -> new IllegalArgumentException("Missing sensor"));
        String hardware = stringValue(json, "hardware")
                .orElseThrow(() -> new IllegalArgumentException("Missing hardware"));
        if (!CpuTemperatureMonitor.isValid(value) || sensor.isBlank() || hardware.isBlank()) {
            return CpuTemperatureReading.unavailable(provider, "INVALID_READING");
        }
        return CpuTemperatureReading.available(value, provider, sensor, hardware);
    }

    private static Optional<String> stringValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"((?:\\\\[\\\"\\\\/bfnrt]|\\\\u[0-9a-fA-F]{4}|[^\\\"\\\\])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Optional.of(unescape(matcher.group(1))) : Optional.empty();
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++index >= value.length()) throw new IllegalArgumentException("Invalid JSON escape");
            char escaped = value.charAt(index);
            switch (escaped) {
                case '\"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) throw new IllegalArgumentException("Invalid Unicode escape");
                    result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    index += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON escape");
            }
        }
        return result.toString();
    }

    private void closeBridge() {
        try {
            bridge.close();
        } catch (Exception ignored) {
            // Failure already degrades safely to N/D.
        }
    }

    private void restartBridge() {
        try {
            bridge.restart();
        } catch (IOException ignored) {
            // The failed response already degrades safely to N/D. A later retry may restart it.
        }
    }

    @Override
    public void close() {
        closeBridge();
    }

    interface HelperBridge extends AutoCloseable {
        String request(Duration timeout) throws IOException, TimeoutException;

        default void restart() throws IOException {
        }

        @Override
        default void close() {
        }
    }
}
