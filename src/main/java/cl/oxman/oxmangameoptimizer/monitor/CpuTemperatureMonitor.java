package cl.oxman.oxmangameoptimizer.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;

/** Validates CPU-specific sensor readings without leaking sensor failures to the dashboard. */
final class CpuTemperatureMonitor implements AutoCloseable {

    static final double MAX_REASONABLE_CELSIUS = 125.0;

    private static final Logger LOG = LoggerFactory.getLogger(CpuTemperatureMonitor.class);

    private final List<CpuTemperatureProvider> providers;
    private final AtomicBoolean providerStatusLogged = new AtomicBoolean();

    CpuTemperatureMonitor(CpuTemperatureProvider... providers) {
        this.providers = List.copyOf(Arrays.asList(providers));
    }

    OptionalDouble readCelsius() {
        CpuTemperatureReading reading = read();
        return reading.temperatureCelsius();
    }

    CpuTemperatureReading read() {
        for (CpuTemperatureProvider provider : providers) {
            try {
                CpuTemperatureReading reading = provider.read();
                if (reading.available() && isValid(reading.temperatureCelsius().getAsDouble())) {
                    logProviderOnce(reading);
                    return reading;
                }
            } catch (RuntimeException | LinkageError exception) {
                // A failed provider must not prevent the next fallback from being attempted.
            }
        }
        CpuTemperatureReading unavailable = CpuTemperatureReading.unavailable("unavailable", "NO_PROVIDER");
        logProviderOnce(unavailable);
        return unavailable;
    }

    static boolean isValid(double value) {
        return Double.isFinite(value) && value > 0.0 && value <= MAX_REASONABLE_CELSIUS;
    }

    private void logProviderOnce(CpuTemperatureReading reading) {
        if (!providerStatusLogged.compareAndSet(false, true)) return;
        if (reading.available()) {
            LOG.info("CPU temperature provider: {}", reading.provider());
            LOG.info("CPU temperature sensor: {}", reading.sensor());
        } else {
            LOG.info("CPU temperature provider: unavailable");
        }
    }

    @Override
    public void close() {
        for (CpuTemperatureProvider provider : providers) {
            try {
                provider.close();
            } catch (RuntimeException ignored) {
                // Shutdown cleanup is best effort.
            }
        }
    }
}
