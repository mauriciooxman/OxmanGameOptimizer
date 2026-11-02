package cl.oxman.oxmangameoptimizer.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowsProcessPriorityServiceTest {
    @Test void acceptsHighAtValidationBoundaryWithoutCallingWindows() {
        assertDoesNotThrow(() -> WindowsProcessPriorityService.validateTarget(ProcessPriority.HIGH));
    }

    @Test void refusesRealtimeBeforeAnyNativeCall() {
        assertThrows(IllegalArgumentException.class,
                () -> WindowsProcessPriorityService.validateTarget(ProcessPriority.REALTIME));
    }
}
