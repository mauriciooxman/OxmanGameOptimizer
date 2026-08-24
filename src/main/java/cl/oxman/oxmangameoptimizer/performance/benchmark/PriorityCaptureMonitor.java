package cl.oxman.oxmangameoptimizer.performance.benchmark;

public interface PriorityCaptureMonitor extends AutoCloseable {
    PriorityCaptureMonitor STABLE = new PriorityCaptureMonitor() {
        @Override public boolean drifted() { return false; }
        @Override public void close() { }
    };

    boolean drifted();
    @Override void close();
}
