package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import cl.oxman.oxmangameoptimizer.system.ProcessPriority;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityController;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityReading;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Observes priority during one capture. It never writes process priority. */
final class ProcessPriorityCaptureMonitor implements PriorityCaptureMonitor {
    private static final ProcessPriority EXPECTED = ProcessPriority.ABOVE_NORMAL;
    private final ProcessPriorityController priorities;
    private final ProcessIdentity identity;
    private final Consumer<String> log;
    private final LongSupplier nanoTime;
    private final long startedAt;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean drifted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile ProcessPriorityReading previous;

    ProcessPriorityCaptureMonitor(ProcessPriorityController priorities, ProcessIdentity identity, Consumer<String> log) {
        this(priorities, identity, log, System::nanoTime, Duration.ofMillis(500));
    }

    ProcessPriorityCaptureMonitor(ProcessPriorityController priorities, ProcessIdentity identity, Consumer<String> log,
            LongSupplier nanoTime, Duration interval) {
        this.priorities = priorities;
        this.identity = identity;
        this.log = log;
        this.nanoTime = nanoTime;
        this.startedAt = nanoTime.getAsLong();
        this.previous = priorities.readWithNativeValue(identity);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "priority-capture-monitor");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::pollSafely, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void poll() {
        ProcessPriorityReading current = priorities.readWithNativeValue(identity);
        ProcessPriorityReading before = previous;
        previous = current;
        if (current.priority() != EXPECTED) drifted.set(true);
        if (current.windowsValue() != before.windowsValue()) {
            double elapsed = (nanoTime.getAsLong() - startedAt) / 1_000_000_000.0;
            log.accept("Priority drift detected:\n" + before.display() + " -> " + current.display()
                    + "\nElapsed: " + String.format(Locale.ROOT, "%.1fs", elapsed));
        }
    }

    private void pollSafely() {
        try { poll(); }
        catch (RuntimeException exception) {
            drifted.set(true);
            log.accept("Priority monitor failed: " + exception.getMessage());
        }
    }

    @Override public boolean drifted() { return drifted.get(); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdownNow();
        try { executor.awaitTermination(1, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        pollSafely();
    }
}
