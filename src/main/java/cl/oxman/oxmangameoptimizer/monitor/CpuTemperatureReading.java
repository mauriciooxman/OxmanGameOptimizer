package cl.oxman.oxmangameoptimizer.monitor;

import java.util.Objects;
import java.util.OptionalDouble;

record CpuTemperatureReading(OptionalDouble temperatureCelsius, String provider,
                             String sensor, String hardware, String reason) {

    CpuTemperatureReading {
        temperatureCelsius = Objects.requireNonNull(temperatureCelsius);
        provider = Objects.requireNonNullElse(provider, "unavailable");
        sensor = Objects.requireNonNullElse(sensor, "");
        hardware = Objects.requireNonNullElse(hardware, "");
        reason = Objects.requireNonNullElse(reason, "");
    }

    static CpuTemperatureReading available(
            double temperatureCelsius, String provider, String sensor, String hardware) {
        return new CpuTemperatureReading(OptionalDouble.of(temperatureCelsius),
                provider, sensor, hardware, "");
    }

    static CpuTemperatureReading unavailable(String provider, String reason) {
        return new CpuTemperatureReading(OptionalDouble.empty(), provider, "", "", reason);
    }

    boolean available() {
        return temperatureCelsius.isPresent();
    }
}
