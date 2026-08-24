package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.time.Duration;

public interface GamePerformanceCapture extends AutoCloseable {
    boolean isAvailable();
    void start(String processName, Duration duration) throws Exception;
    GamePerformanceResult stop() throws Exception;
    @Override void close();
}
