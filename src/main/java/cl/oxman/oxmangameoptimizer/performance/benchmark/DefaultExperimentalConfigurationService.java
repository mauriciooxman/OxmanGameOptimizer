package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationEngine;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.optimizer.action.ProcessPriorityAction;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityController;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.io.IOException;
import java.util.ArrayList;

public final class DefaultExperimentalConfigurationService implements ExperimentalConfigurationService {
    private final BenchmarkBoostService safeBoost;
    private final ProcessPriorityController priorities;
    private final Consumer<String> log;
    private OptimizationEngine priorityEngine;
    private boolean safeActive;

    public DefaultExperimentalConfigurationService(BenchmarkBoostService safeBoost,
            ProcessPriorityController priorities, Consumer<String> log) {
        this.safeBoost = safeBoost; this.priorities = priorities; this.log = log;
    }

    @Override public synchronized OptimizationReport apply(GameProfile game, ExperimentConfiguration configuration) {
        safeActive = true;
        OptimizationReport safe = safeBoost.apply(game.toString()).join();
        if (!configuration.usesPriority()) return safe;
        var identity = game.findRunningProcessIdentity().orElseThrow(() ->
                new IllegalStateException("No se pudo identificar un único proceso del juego"));
        priorityEngine = new OptimizationEngine(List.of(new ProcessPriorityAction(identity.pid(), priorities)),
                new SessionStateStore(recoveryPath()), log, true);
        OptimizationReport priority = priorityEngine.apply(game.toString());
        if (priority.failed() > 0) throw new IllegalStateException("No se pudo aplicar ABOVE_NORMAL");
        return new OptimizationReport(safe.applicable() + priority.applicable(), safe.applied() + priority.applied(),
                safe.failed() + priority.failed(), false);
    }

    @Override public synchronized OptimizationReport restore() {
        int applicable = 0, restored = 0, failed = 0; boolean complete = true;
        if (priorityEngine != null) {
            OptimizationReport value = priorityEngine.restore();
            applicable += value.applicable(); restored += value.applied(); failed += value.failed(); complete &= value.fullyRestored();
            priorityEngine = null;
        }
        if (safeActive) {
            OptimizationReport value = safeBoost.restore().join();
            applicable += value.applicable(); restored += value.applied(); failed += value.failed(); complete &= value.fullyRestored();
            safeActive = false;
        }
        return new OptimizationReport(applicable, restored, failed, complete);
    }
    public static boolean recoverIncomplete(ProcessPriorityController priorities, Consumer<String> log) {
        SessionStateStore store = new SessionStateStore(recoveryPath());
        try {
            var state = store.load();
            if (state.isEmpty()) return true;
            List<cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationAction> actions = new ArrayList<>();
            for (var change : state.get().changes()) {
                if (!change.actionId().startsWith("process-priority:")) continue;
                long pid = Long.parseLong(change.actionId().substring("process-priority:".length()));
                actions.add(new ProcessPriorityAction(pid, priorities));
            }
            if (actions.isEmpty()) return false;
            return new OptimizationEngine(actions, store, log, true).restore().fullyRestored();
        } catch (IOException | RuntimeException exception) {
            log.accept("⚠ No se pudo recuperar prioridad experimental: " + exception.getMessage());
            return false;
        }
    }

    private static Path recoveryPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return base.resolve("OxmanGameOptimizer").resolve("priority-experiment-state.json");
    }
}
