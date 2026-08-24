package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import cl.oxman.oxmangameoptimizer.system.ProcessPowerThrottlingService;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Detects external HighQoS changes during a capture; it never reapplies policy. */
public final class ProcessPowerThrottlingCaptureMonitor implements PriorityCaptureMonitor {
    private final ScheduledExecutorService executor;
    private final AtomicBoolean drifted = new AtomicBoolean();
    public ProcessPowerThrottlingCaptureMonitor(ProcessPowerThrottlingService service, ProcessIdentity identity, Consumer<String> log) {
        this(service, identity, log, Duration.ofSeconds(1));
    }
    ProcessPowerThrottlingCaptureMonitor(ProcessPowerThrottlingService service, ProcessIdentity identity,
            Consumer<String> log, Duration interval) {
        executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "high-qos-monitor"); t.setDaemon(true); return t; });
        executor.scheduleAtFixedRate(() -> {
            try {
                if (!service.isSameProcess(identity) || !service.verifyHighQos(identity)) drifted.set(true);
            } catch (RuntimeException exception) {
                drifted.set(true); log.accept("Power throttling monitor: " + exception.getMessage());
            }
        }, 0, Math.max(1, interval.toMillis()), TimeUnit.MILLISECONDS);
    }
    public boolean drifted() { return drifted.get(); }
    public void close() { executor.shutdownNow(); }
}
