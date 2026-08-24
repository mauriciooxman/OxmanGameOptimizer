package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ProcessPriorityCaptureMonitorTest {
    private static final ProcessIdentity IDENTITY = new ProcessIdentity(42, 1234, "game.exe");

    @Test void aboveNormalRemainsStableWithoutLogging() {
        FakePriority priority = new FakePriority(); List<String> logs = new ArrayList<>();
        try (var monitor = monitor(priority, logs, new AtomicLong())) {
            monitor.poll(); monitor.poll();
            assertFalse(monitor.drifted());
        }
        assertTrue(logs.isEmpty());
    }

    @Test void detectsHighAndLogsOnlyTheChangeWithElapsedTime() {
        FakePriority priority = new FakePriority(); List<String> logs = new ArrayList<>();
        AtomicLong clock = new AtomicLong();
        try (var monitor = monitor(priority, logs, clock)) {
            monitor.poll();
            priority.value = ProcessPriority.HIGH; clock.set(4_500_000_000L);
            monitor.poll(); monitor.poll();
            assertTrue(monitor.drifted());
        }
        assertEquals(1, logs.size());
        assertEquals("Priority drift detected:\nABOVE_NORMAL (0x00008000) -> HIGH (0x00000080)\nElapsed: 4.5s", logs.getFirst());
        assertEquals(0, priority.sets);
    }

    @Test void detectsNormalDuringCapture() {
        FakePriority priority = new FakePriority();
        try (var monitor = monitor(priority, new ArrayList<>(), new AtomicLong())) {
            priority.value = ProcessPriority.NORMAL; monitor.poll();
            assertTrue(monitor.drifted());
        }
    }

    @Test void closeCancelsMonitorThreadCleanly() {
        FakePriority priority = new FakePriority();
        var monitor = monitor(priority, new ArrayList<>(), new AtomicLong());
        monitor.close();
        int reads = priority.reads;
        assertTimeoutPreemptively(Duration.ofMillis(100), monitor::close);
        assertEquals(reads, priority.reads);
    }

    private static ProcessPriorityCaptureMonitor monitor(FakePriority priority, List<String> logs, AtomicLong clock) {
        return new ProcessPriorityCaptureMonitor(priority, IDENTITY, logs::add, clock::get, Duration.ofHours(1));
    }

    private static final class FakePriority implements ProcessPriorityController {
        ProcessPriority value = ProcessPriority.ABOVE_NORMAL; int reads; int sets;
        public ProcessIdentity identify(long processId) { return IDENTITY; }
        public ProcessPriority read(ProcessIdentity process) { reads++; return value; }
        public void set(ProcessIdentity process, ProcessPriority priority) { sets++; value = priority; }
        public boolean isSameProcess(ProcessIdentity process) { return true; }
    }
}
