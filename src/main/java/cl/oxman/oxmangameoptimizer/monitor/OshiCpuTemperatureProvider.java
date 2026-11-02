package cl.oxman.oxmangameoptimizer.monitor;

import java.util.function.DoubleSupplier;

final class OshiCpuTemperatureProvider implements CpuTemperatureProvider {

    private final DoubleSupplier sensor;
    private final boolean cpuSpecificSource;

    OshiCpuTemperatureProvider(DoubleSupplier sensor, boolean cpuSpecificSource) {
        this.sensor = sensor;
        this.cpuSpecificSource = cpuSpecificSource;
    }

    @Override
    public CpuTemperatureReading read() {
        if (!cpuSpecificSource) {
            return CpuTemperatureReading.unavailable("OSHI", "UNVERIFIED_WINDOWS_SOURCE");
        }

        try {
            double value = sensor.getAsDouble();
            return CpuTemperatureMonitor.isValid(value)
                    ? CpuTemperatureReading.available(value, "OSHI", "CPU", "")
                    : CpuTemperatureReading.unavailable("OSHI", "INVALID_READING");
        } catch (RuntimeException | LinkageError exception) {
            return CpuTemperatureReading.unavailable("OSHI", "READ_FAILED");
        }
    }
}
