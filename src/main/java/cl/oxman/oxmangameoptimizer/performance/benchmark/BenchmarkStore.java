package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Persists aggregate metrics only: no usernames, credentials, command lines or install paths. */
public final class BenchmarkStore {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneId.systemDefault());
    private final Path directory;
    public BenchmarkStore(Path directory) { this.directory = directory; }
    public static BenchmarkStore localAppData() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return new BenchmarkStore(base.resolve("OxmanGameOptimizer").resolve("benchmarks"));
    }
    public Path save(BenchmarkRecord record) throws IOException {
        Files.createDirectories(directory);
        String safeGame = record.processName().replaceAll("(?i)\\.exe$", "").replaceAll("[^a-zA-Z0-9_-]", "_");
        Path file = directory.resolve(FILE_TIME.format(record.timestamp()) + "_" + safeGame + ".json");
        String json = "{\n" + text("configurationId", record.configurationId()) + ",\n" + text("gameName", record.gameName()) + ",\n" + text("processName", record.processName()) +
                ",\n  \"timestamp\": \"" + record.timestamp() + "\",\n  \"activeOptimizations\": " + record.activeOptimizations() +
                ",\n" + text("configurationA", record.configurationA()) +
                ",\n" + text("configurationB", record.configurationB()) +
                ",\n" + text("experimentType", record.experimentType()) +
                ",\n  \"runNumber\": " + record.runNumber() +
                ",\n" + text("runOrder", record.runOrder()) +
                ",\n  \"activeOptimizationNames\": [" + record.activeOptimizationNames().stream()
                .map(value -> "\"" + escape(value) + "\"").collect(java.util.stream.Collectors.joining(",")) + "]" +
                ",\n  \"captureDurationSeconds\": " + record.captureDuration().toSeconds() +
                ",\n  \"optimizationsApplicable\": " + record.optimizationReport().applicable() +
                ",\n  \"optimizationsApplied\": " + record.optimizationReport().applied() +
                ",\n  \"optimizationsFailed\": " + record.optimizationReport().failed() +
                ",\n" + text("oxmanVersion", record.oxmanVersion()) + ",\n" + text("windowsVersion", record.windowsVersion()) +
                ",\n  \"before\": " + gameResult(record.before()) + ",\n  \"after\": " + gameResult(record.after()) +
                ",\n  \"systemBefore\": " + systemResult(record.systemBefore()) +
                ",\n  \"systemAfter\": " + systemResult(record.systemAfter()) + "\n}\n";
        return Files.writeString(file, json, StandardCharsets.UTF_8);
    }
    private static String gameResult(GamePerformanceResult value) {
        if (value == null) return "null";
        return "{\"averageFps\":" + number(value.averageFps()) + ",\"onePercentLow\":" + number(value.onePercentLow()) +
                ",\"averageFrameTimeMs\":" + number(value.averageFrameTimeMs()) + ",\"frameCount\":" + value.frameCount() + "}";
    }
    private static String number(java.util.OptionalDouble value) { return value.isPresent() ? Double.toString(value.getAsDouble()) : "null"; }
    private static String systemResult(cl.oxman.oxmangameoptimizer.performance.PerformanceSnapshot value) {
        if (value == null) return "null";
        return "{\"cpuAverage\":" + value.cpuAverage() + ",\"ramUsedAverage\":" + value.ramUsedAverage()
                + ",\"processCountAverage\":" + value.processCountAverage() + ",\"sampleCount\":" + value.sampleCount() + "}";
    }
    private static String text(String key, String value) { return "  \"" + key + "\": \"" + escape(value) + "\""; }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " "); }
}
