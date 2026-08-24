package cl.oxman.oxmangameoptimizer.performance;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface BenchmarkSystemSampler { CompletableFuture<PerformanceSnapshot> sampleSystem(); }
