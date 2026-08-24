package cl.oxman.oxmangameoptimizer.performance.benchmark;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

public class PresentMonCsvParser {
    public GamePerformanceResult parse(Reader input, String processName, Duration duration) throws IOException {
        var records = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(input);
        List<Double> frameTimes = new ArrayList<>(), cpuTimes = new ArrayList<>(), gpuTimes = new ArrayList<>();
        for (CSVRecord record : records) {
            add(record, frameTimes, "MsBetweenAppStart", "FrameTime", "MsBetweenPresents", "DisplayedTime");
            add(record, cpuTimes, "MsCPUBusy", "CPUBusy", "CPUFrameTime", "msCPUBusy");
            add(record, gpuTimes, "MsGPUTime", "GPUTime", "MsGPUBusy", "GPUBusy", "GPUFrameTime", "msGPUActive");
        }
        OptionalDouble averageFrame = average(frameTimes);
        OptionalDouble fps = averageFrame.isPresent() && averageFrame.getAsDouble() > 0
                ? OptionalDouble.of(1000.0 / averageFrame.getAsDouble()) : OptionalDouble.empty();
        return new GamePerformanceResult(fps, FrameStatistics.onePercentLowFps(frameTimes), averageFrame,
                average(cpuTimes), average(gpuTimes), frameTimes.size(), duration, processName, Instant.now());
    }
    private static void add(CSVRecord record, List<Double> target, String... aliases) {
        Map<String, String> values = record.toMap();
        for (String alias : aliases) if (values.containsKey(alias)) {
            try { double value = Double.parseDouble(values.get(alias)); if (Double.isFinite(value) && value > 0) target.add(value); }
            catch (NumberFormatException ignored) { }
            return;
        }
    }
    private static OptionalDouble average(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).average(); }
}
