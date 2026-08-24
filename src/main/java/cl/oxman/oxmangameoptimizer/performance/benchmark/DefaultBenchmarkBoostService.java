package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.optimizer.BoostOptimizer;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import java.util.concurrent.CompletableFuture;

public final class DefaultBenchmarkBoostService implements BenchmarkBoostService {
    public CompletableFuture<OptimizationReport> apply(String gameName) { return BoostOptimizer.applyBoostAsync(gameName); }
    public CompletableFuture<OptimizationReport> restore() { return BoostOptimizer.restoreAsync(); }
}
