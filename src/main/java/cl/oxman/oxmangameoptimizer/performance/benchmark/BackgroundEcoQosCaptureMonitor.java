package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Detects external changes to any EcoQoS process without reapplying policy. */
final class BackgroundEcoQosCaptureMonitor implements PriorityCaptureMonitor {
    private final ScheduledExecutorService executor;
    private final AtomicBoolean drifted = new AtomicBoolean();
    BackgroundEcoQosCaptureMonitor(ProcessPowerThrottlingService service, List<ProcessIdentity> identities,
            Consumer<String> log) { this(service, identities, log, Duration.ofSeconds(1)); }
    BackgroundEcoQosCaptureMonitor(ProcessPowerThrottlingService service, List<ProcessIdentity> identities,
            Consumer<String> log, Duration interval) {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "background-ecoqos-monitor"); thread.setDaemon(true); return thread;
        });
        executor.scheduleAtFixedRate(() -> {
            for (ProcessIdentity identity : identities) try {
                if (!service.isSameProcess(identity) || !service.verifyEcoQos(identity)) drifted.set(true);
            } catch (RuntimeException exception) {
                drifted.set(true); log.accept("Background EcoQoS monitor: " + exception.getMessage());
            }
        }, 0, Math.max(1, interval.toMillis()), TimeUnit.MILLISECONDS);
    }
    public boolean drifted() { return drifted.get(); }
    public void close() { executor.shutdownNow(); }
}
