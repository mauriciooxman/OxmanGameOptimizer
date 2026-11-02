package cl.oxman.oxmangameoptimizer.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareMonitorInitializationProbeTest {
    @Test
    void initializesOshiWithRuntimeJna() {
        assertTrue(Double.isFinite(HardwareMonitor.getCpuUsage()));
    }

    @Test
    void existingCpuRamAndGpuMetricsRemainReadable() {
        assertTrue(Double.isFinite(HardwareMonitor.getRamUsage()));
        assertTrue(Double.isFinite(HardwareMonitor.getUsedRamGB()));
        assertTrue(Double.isFinite(HardwareMonitor.getTotalRamGB()));
        assertNotNull(assertDoesNotThrow(HardwareMonitor::getCpuName));
        assertNotNull(assertDoesNotThrow(HardwareMonitor::getGpuName));
    }

    @Test
    void classifiesRawTemperatureWithoutDependingOnPhysicalSensor() {
        assertEquals("ZERO", HardwareMonitor.describeRawTemperature(0.0));
        assertEquals("NaN", HardwareMonitor.describeRawTemperature(Double.NaN));
        assertEquals("NEGATIVE", HardwareMonitor.describeRawTemperature(-1.0));
        assertEquals("POSITIVE_INFINITY", HardwareMonitor.describeRawTemperature(Double.POSITIVE_INFINITY));
        assertEquals("POSITIVE_FINITE", HardwareMonitor.describeRawTemperature(54.0));
    }
}
