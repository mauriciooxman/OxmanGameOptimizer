package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;

public interface ExperimentalConfigurationService {
    OptimizationReport apply(GameProfile game, ExperimentConfiguration configuration);
    OptimizationReport restore();
}
