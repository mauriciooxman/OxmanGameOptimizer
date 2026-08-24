package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;

public interface ExperimentalConfigurationService {
    ConfigurationApplication apply(GameProfile game, ExperimentConfiguration configuration);
    default PriorityCaptureMonitor monitorPriorityDuringCapture() { return PriorityCaptureMonitor.STABLE; }
    default PriorityCaptureMonitor monitorExperimentalConfigurationDuringCapture() { return monitorPriorityDuringCapture(); }
    OptimizationReport restore();
}
