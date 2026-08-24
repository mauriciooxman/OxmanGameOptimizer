package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import java.util.concurrent.CompletableFuture;

public interface BenchmarkBoostService {
    CompletableFuture<OptimizationReport> apply(String gameName);
    CompletableFuture<OptimizationReport> restore();
}
