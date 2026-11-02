package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import java.util.concurrent.CompletableFuture;

public interface BenchmarkBoostService {
    default CompletableFuture<BaselinePreparation> prepareBaseline() {
        return CompletableFuture.completedFuture(BaselinePreparation.completed());
    }
    CompletableFuture<OptimizationReport> apply(String gameName);
    default CompletableFuture<OptimizationReport> apply(GameProfile game) { return apply(game.toString()); }
    CompletableFuture<OptimizationReport> restore();

    record BaselinePreparation(boolean ready, String detail) {
        public static BaselinePreparation completed() { return new BaselinePreparation(true, "Estado base verificado"); }
        public static BaselinePreparation blocked(String detail) { return new BaselinePreparation(false, detail); }
    }
}
