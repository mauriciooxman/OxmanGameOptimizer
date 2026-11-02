package cl.oxman.oxmangameoptimizer.monitor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibreHardwareMonitorCpuTemperatureProviderTest {

    private static final String VALID_RESPONSE = """
            {"available":true,"temperatureCelsius":58.4,"sensor":"CPU Package",\
            "hardware":"Intel Core i7-11800H","provider":"LibreHardwareMonitor","reason":null}
            """;

    @Test
    void parsesValidCpuPackageReading() {
        var provider = providerReturning(VALID_RESPONSE);

        CpuTemperatureReading reading = provider.read();

        assertEquals(58.4, reading.temperatureCelsius().orElseThrow());
        assertEquals("CPU Package", reading.sensor());
        assertEquals("Intel Core i7-11800H", reading.hardware());
    }

    @Test
    void unavailableHelperReturnsNoTemperature() {
        var provider = providerReturning("""
                {"available":false,"temperatureCelsius":null,"sensor":null,"hardware":null,\
                "provider":"LibreHardwareMonitor","reason":"PAWNIO_NOT_INSTALLED"}
                """);

        CpuTemperatureReading reading = provider.read();

        assertTrue(reading.temperatureCelsius().isEmpty());
        assertEquals("PAWNIO_NOT_INSTALLED", reading.reason());
    }

    @Test
    void timeoutReturnsNoTemperature() {
        var provider = new LibreHardwareMonitorCpuTemperatureProvider(timeout -> {
            throw new TimeoutException("slow helper");
        });

        CpuTemperatureReading reading = provider.read();

        assertTrue(reading.temperatureCelsius().isEmpty());
        assertEquals("TIMEOUT", reading.reason());
    }

    @Test
    void invalidJsonReturnsNoTemperature() {
        var provider = providerReturning("not-json");

        CpuTemperatureReading reading = provider.read();

        assertTrue(reading.temperatureCelsius().isEmpty());
        assertEquals("INVALID_JSON", reading.reason());
    }

    @Test
    void malformedResponseRestartsBridgeAndRecoversAfterBackoff() {
        AtomicLong time = new AtomicLong();
        SequenceBridge bridge = new SequenceBridge("not-json", VALID_RESPONSE);
        var provider = new LibreHardwareMonitorCpuTemperatureProvider(bridge, time::get);

        assertEquals("INVALID_JSON", provider.read().reason());
        assertEquals(1, bridge.restarts);
        assertEquals(1, bridge.requests);

        assertEquals("INVALID_JSON", provider.read().reason());
        assertEquals(1, bridge.requests, "backoff must prevent a tight restart loop");

        time.addAndGet(LibreHardwareMonitorCpuTemperatureProvider.FAILURE_RETRY_DELAY.toNanos());
        assertEquals(58.4, provider.read().temperatureCelsius().orElseThrow());
        assertEquals(2, bridge.requests);
    }

    @Test
    void timeoutCanRecoverOnNextScheduledAttempt() {
        AtomicLong time = new AtomicLong();
        SequenceBridge bridge = new SequenceBridge(
                new TimeoutException("slow helper"), VALID_RESPONSE);
        var provider = new LibreHardwareMonitorCpuTemperatureProvider(bridge, time::get);

        assertEquals("TIMEOUT", provider.read().reason());
        time.addAndGet(LibreHardwareMonitorCpuTemperatureProvider.FAILURE_RETRY_DELAY.toNanos());

        assertEquals(58.4, provider.read().temperatureCelsius().orElseThrow());
    }

    @Test
    void exitedProcessCanRecoverOnNextScheduledAttempt() {
        AtomicLong time = new AtomicLong();
        SequenceBridge bridge = new SequenceBridge(
                new IOException("helper exited"), VALID_RESPONSE);
        var provider = new LibreHardwareMonitorCpuTemperatureProvider(bridge, time::get);

        assertEquals("PROCESS_FAILED", provider.read().reason());
        time.addAndGet(LibreHardwareMonitorCpuTemperatureProvider.FAILURE_RETRY_DELAY.toNanos());

        assertEquals(58.4, provider.read().temperatureCelsius().orElseThrow());
    }

    @Test
    void failedProcessReturnsNoTemperature() {
        var provider = new LibreHardwareMonitorCpuTemperatureProvider(timeout -> {
            throw new IOException("process failed");
        });

        CpuTemperatureReading reading = provider.read();

        assertTrue(reading.temperatureCelsius().isEmpty());
        assertEquals("PROCESS_FAILED", reading.reason());
    }

    @Test
    void zeroIsNeverAcceptedAsTemperature() {
        var provider = providerReturning("""
                {"available":true,"temperatureCelsius":0,"sensor":"CPU Package",\
                "hardware":"Intel CPU","provider":"LibreHardwareMonitor"}
                """);

        CpuTemperatureReading reading = provider.read();

        assertTrue(reading.temperatureCelsius().isEmpty());
        assertEquals("INVALID_READING", reading.reason());
    }

    private static LibreHardwareMonitorCpuTemperatureProvider providerReturning(String response) {
        return new LibreHardwareMonitorCpuTemperatureProvider(timeout -> response);
    }

    private static final class SequenceBridge
            implements LibreHardwareMonitorCpuTemperatureProvider.HelperBridge {
        private final Deque<Object> responses = new ArrayDeque<>();
        private int requests;
        private int restarts;

        private SequenceBridge(Object... responses) {
            this.responses.addAll(java.util.List.of(responses));
        }

        @Override
        public String request(Duration timeout) throws IOException, TimeoutException {
            requests++;
            Object response = responses.removeFirst();
            if (response instanceof IOException exception) throw exception;
            if (response instanceof TimeoutException exception) throw exception;
            return (String) response;
        }

        @Override
        public void restart() {
            restarts++;
        }
    }
}
