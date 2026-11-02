package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.game.GamingSessionManager;
import cl.oxman.oxmangameoptimizer.optimizer.BoostOptimizer;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import java.util.concurrent.CompletableFuture;

public final class DefaultBenchmarkBoostService implements BenchmarkBoostService {
    @Override public CompletableFuture<BaselinePreparation> prepareBaseline() {
        if (GamingSessionManager.isSessionActive()) return CompletableFuture.completedFuture(
                BaselinePreparation.blocked("Existe una sesión BOOST activa"));
        return BoostOptimizer.recoverIncompleteSessionAsync().handle((restored, error) -> {
            if (error != null) return BaselinePreparation.blocked("Falló la recuperación BOOST: " + error.getMessage());
            return restored ? BaselinePreparation.completed()
                    : BaselinePreparation.blocked("La recuperación BOOST quedó pendiente; snapshot conservado");
        });
    }
    public CompletableFuture<OptimizationReport> apply(String gameName) { return BoostOptimizer.applyBoostAsync(gameName); }
    @Override public CompletableFuture<OptimizationReport> apply(GameProfile game) { return BoostOptimizer.applyBoostAsync(game); }
    public CompletableFuture<OptimizationReport> restore() { return BoostOptimizer.restoreAsync(); }
}
