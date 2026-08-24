package cl.oxman.oxmangameoptimizer.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainControllerBenchmarkDispatchTest {
    @Test
    void exposesAllBenchmarkModesWithUnambiguousLabels() {
        assertEquals(List.of("BOOST NORMAL", "EXPERIMENTO: PRIORIDAD", "EXPERIMENTO: HIGH QOS", "EXPERIMENTO: CARGA DE FONDO"),
                List.of(BenchmarkMode.values()).stream().map(BenchmarkMode::toString).toList());
    }

    @Test
    void normalSelectionDispatchesOnlyNormalBenchmark() {
        assertDispatch(BenchmarkMode.NORMAL, 1, 0, 0, 0);
    }

    @Test
    void prioritySelectionDispatchesOnlyProcessPriorityExperiment() {
        assertDispatch(BenchmarkMode.PROCESS_PRIORITY, 0, 1, 0, 0);
    }

    @Test
    void highQosSelectionDispatchesOnlyPowerThrottlingExperiment() {
        assertDispatch(BenchmarkMode.HIGH_QOS, 0, 0, 1, 0);
    }

    @Test void backgroundSelectionDispatchesOnlyBackgroundExperiment() {
        assertDispatch(BenchmarkMode.BACKGROUND, 0, 0, 0, 1);
    }

    private static void assertDispatch(BenchmarkMode mode, int normalExpected, int priorityExpected,
            int highQosExpected, int backgroundExpected) {
        AtomicInteger normal = new AtomicInteger();
        AtomicInteger priority = new AtomicInteger();
        AtomicInteger highQos = new AtomicInteger();
        AtomicInteger background = new AtomicInteger();

        MainController.dispatchBenchmark(mode,
                () -> completed(normal), () -> completed(priority), () -> completed(highQos), () -> completed(background)).join();

        assertEquals(normalExpected, normal.get());
        assertEquals(priorityExpected, priority.get());
        assertEquals(highQosExpected, highQos.get());
        assertEquals(backgroundExpected, background.get());
    }

    private static CompletableFuture<Void> completed(AtomicInteger counter) {
        counter.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    }
}
