package cl.oxman.oxmangameoptimizer.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowsProcessPriorityServiceTest {
    private final WindowsProcessPriorityService service = new WindowsProcessPriorityService();
    private final ProcessIdentity process = new ProcessIdentity(1, 1, "test.exe");

    @Test void refusesHighBeforeAnyNativeCall() {
        assertThrows(IllegalArgumentException.class, () -> service.set(process, ProcessPriority.HIGH));
    }

    @Test void refusesRealtimeBeforeAnyNativeCall() {
        assertThrows(IllegalArgumentException.class, () -> service.set(process, ProcessPriority.REALTIME));
    }
}
