package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;

public interface BackgroundLoadGuard {
    ConfigurationApplication apply(GameProfile game);
    PriorityCaptureMonitor monitor();
    OptimizationReport restore();
}
