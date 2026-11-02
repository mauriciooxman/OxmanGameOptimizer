package cl.oxman.oxmangameoptimizer.monitor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuTemperatureMonitorTest {

    @Test
    void validOshiReadingDoesNotUseFallback() {
        AtomicInteger fallbackReads = new AtomicInteger();
        var monitor = new CpuTemperatureMonitor(
                () -> CpuTemperatureReading.available(54.25, "OSHI", "CPU", "Processor"),
                () -> {
                    fallbackReads.incrementAndGet();
                    return CpuTemperatureReading.available(
                            55.0, "LibreHardwareMonitor", "CPU Package", "Processor");
                });

        assertEquals(54.25, monitor.readCelsius().orElseThrow());
        assertEquals(0, fallbackReads.get());
    }

    @Test
    void unavailableOshiUsesLibreHardwareMonitorFallback() {
        var monitor = new CpuTemperatureMonitor(
                () -> CpuTemperatureReading.unavailable("OSHI", "UNAVAILABLE"),
                () -> CpuTemperatureReading.available(
                        61.0, "LibreHardwareMonitor", "CPU Package", "Intel CPU"));

        CpuTemperatureReading reading = monitor.read();

        assertEquals(61.0, reading.temperatureCelsius().orElseThrow());
        assertEquals("LibreHardwareMonitor", reading.provider());
        assertEquals("CPU Package", reading.sensor());
    }

    @Test
    void unavailableProvidersReturnNoTemperature() {
        var monitor = new CpuTemperatureMonitor(
                () -> CpuTemperatureReading.unavailable("OSHI", "UNAVAILABLE"),
                () -> CpuTemperatureReading.unavailable("LibreHardwareMonitor", "NO_RELIABLE_CPU_SENSOR"));

        assertTrue(monitor.readCelsius().isEmpty());
    }

    @Test
    void rejectsNanInfiniteAndOutOfRangeReadings() {
        assertTrue(monitorWithValue(Double.NaN).readCelsius().isEmpty());
        assertTrue(monitorWithValue(Double.POSITIVE_INFINITY).readCelsius().isEmpty());
        assertTrue(monitorWithValue(0.0).readCelsius().isEmpty());
        assertTrue(monitorWithValue(-1.0).readCelsius().isEmpty());
        assertTrue(monitorWithValue(125.01).readCelsius().isEmpty());
    }

    @Test
    void providerExceptionDoesNotPreventFallback() {
        var monitor = new CpuTemperatureMonitor(
                () -> { throw new IllegalStateException("temporary sensor failure"); },
                () -> CpuTemperatureReading.available(
                        59.0, "LibreHardwareMonitor", "Core Max", "Intel CPU"));

        assertEquals(59.0, monitor.readCelsius().orElseThrow());
    }

    private static CpuTemperatureMonitor monitorWithValue(double value) {
        return new CpuTemperatureMonitor(
                () -> CpuTemperatureReading.available(value, "test", "CPU Package", "CPU"));
    }
}
