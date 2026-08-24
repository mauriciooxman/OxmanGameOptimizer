package cl.oxman.oxmangameoptimizer.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareMonitorInitializationProbeTest {
    @Test
    void initializesOshiWithRuntimeJna() {
        assertTrue(Double.isFinite(HardwareMonitor.getCpuUsage()));
    }
}
