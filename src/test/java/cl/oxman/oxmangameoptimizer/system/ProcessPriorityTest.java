package cl.oxman.oxmangameoptimizer.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessPriorityTest {
    @Test void mapsEveryDocumentedWin32PriorityClassInBothDirections() {
        assertMapping(ProcessPriority.IDLE, 0x00000040);
        assertMapping(ProcessPriority.BELOW_NORMAL, 0x00004000);
        assertMapping(ProcessPriority.NORMAL, 0x00000020);
        assertMapping(ProcessPriority.ABOVE_NORMAL, 0x00008000);
        assertMapping(ProcessPriority.HIGH, 0x00000080);
        assertMapping(ProcessPriority.REALTIME, 0x00000100);
    }

    @Test void aboveNormalCanNeverBeDecodedAsHigh() {
        assertNotEquals(ProcessPriority.HIGH.windowsValue(), ProcessPriority.ABOVE_NORMAL.windowsValue());
        assertSame(ProcessPriority.ABOVE_NORMAL,
                ProcessPriority.fromWindowsValue(ProcessPriority.ABOVE_NORMAL.windowsValue()));
    }

    @Test void rejectsUnknownNativePriorityClass() {
        assertThrows(IllegalArgumentException.class, () -> ProcessPriority.fromWindowsValue(0x12345678));
    }

    private static void assertMapping(ProcessPriority priority, int nativeValue) {
        assertEquals(nativeValue, priority.windowsValue());
        assertSame(priority, ProcessPriority.fromWindowsValue(nativeValue));
    }
}
