package cl.oxman.oxmangameoptimizer.ui;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpuTemperaturePresentationTest {

    @Test
    void formatsAvailableTemperature() {
        assertEquals("Temperatura: 54 °C", CpuTemperaturePresentation.format(OptionalDouble.of(54.2)));
    }

    @Test
    void formatsUnavailableTemperatureAsNd() {
        assertEquals("Temperatura: N/D", CpuTemperaturePresentation.format(OptionalDouble.empty()));
    }
}
