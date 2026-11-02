package cl.oxman.oxmangameoptimizer.ui;

import java.util.Locale;
import java.util.OptionalDouble;

final class CpuTemperaturePresentation {

    private CpuTemperaturePresentation() {
    }

    static String format(OptionalDouble temperature) {
        return temperature.isPresent()
                ? String.format(Locale.ROOT, "Temperatura: %.0f °C", temperature.getAsDouble())
                : "Temperatura: N/D";
    }
}
