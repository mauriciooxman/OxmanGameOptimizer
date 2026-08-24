package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationEngine;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.optimizer.action.ProcessPriorityAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.ProcessPowerThrottlingAction;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityController;
import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import cl.oxman.oxmangameoptimizer.system.ProcessPowerThrottlingService;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.io.IOException;
import java.util.ArrayList;

public final class DefaultExperimentalConfigurationService implements ExperimentalConfigurationService {
    private final BenchmarkBoostService safeBoost;
    private final ProcessPriorityController priorities;
    private final ProcessPowerThrottlingService powerThrottling;
    private final BackgroundLoadGuard backgroundLoadGuard;
    private final Consumer<String> log;
    private OptimizationEngine priorityEngine;
    private ProcessIdentity priorityIdentity;
    private OptimizationEngine powerThrottlingEngine;
    private ProcessIdentity powerThrottlingIdentity;
    private boolean safeActive;

    public DefaultExperimentalConfigurationService(BenchmarkBoostService safeBoost,
            ProcessPriorityController priorities, Consumer<String> log) {
        this.safeBoost = safeBoost; this.priorities = priorities; this.powerThrottling = null; this.backgroundLoadGuard = null; this.log = log;
    }
    public DefaultExperimentalConfigurationService(BenchmarkBoostService safeBoost,
            ProcessPowerThrottlingService powerThrottling, Consumer<String> log) {
        this.safeBoost = safeBoost; this.priorities = null; this.powerThrottling = powerThrottling; this.backgroundLoadGuard = null; this.log = log;
    }
    public DefaultExperimentalConfigurationService(BenchmarkBoostService safeBoost,
            BackgroundLoadGuard backgroundLoadGuard, Consumer<String> log) {
        this.safeBoost = safeBoost; this.priorities = null; this.powerThrottling = null;
        this.backgroundLoadGuard = backgroundLoadGuard; this.log = log;
    }

    @Override public synchronized ConfigurationApplication apply(GameProfile game, ExperimentConfiguration configuration) {
        safeActive = true;
        OptimizationReport safe = safeBoost.apply(game.toString()).join();
        if (!configuration.isExperimental()) return ConfigurationApplication.valid(safe);
        if (configuration.usesBackgroundEcoQos()) {
            if (backgroundLoadGuard == null) throw new IllegalStateException("Background Load Guard no está configurado");
            ConfigurationApplication background = backgroundLoadGuard.apply(game);
            return new ConfigurationApplication(combine(safe, background.report()), background.validity());
        }
        var identity = game.findRunningProcessIdentity().orElseThrow(() ->
                new IllegalStateException("No se pudo identificar un único proceso del juego"));
        if (configuration.usesHighQos()) {
            if (powerThrottling == null) throw new IllegalStateException("HighQoS no está configurado");
            powerThrottlingIdentity = powerThrottling.identify(identity.pid());
            ProcessPowerThrottlingAction action = new ProcessPowerThrottlingAction(identity.pid(), powerThrottling, log);
            powerThrottlingEngine = new OptimizationEngine(
                    List.of(action),
                    new SessionStateStore(highQosRecoveryPath()), log, true);
            OptimizationReport highQos = powerThrottlingEngine.apply(game.toString());
            if (highQos.failed() > 0) throw new IllegalStateException("No se pudo aplicar HighQoS");
            if (!action.wasAlreadyHighQos() && !action.changedHighQos())
                throw new IllegalStateException("No se pudo determinar el estado de HighQoS");
            OptimizationReport combined = combine(safe, highQos);
            if (action.wasAlreadyHighQos()) {
                log.accept("ℹ HighQoS ya estaba activo en el proceso.");
                log.accept("ℹ No se aplicó ninguna modificación experimental.");
                log.accept("ℹ El experimento no puede evaluar un efecto de rendimiento.");
            }
            return new ConfigurationApplication(combined, action.wasAlreadyHighQos()
                    ? ConfigurationValidity.NO_CHANGE : ConfigurationValidity.VALID);
        }
        if (priorities == null) throw new IllegalStateException("ABOVE_NORMAL no está configurado");
        priorityIdentity = identity;
        priorityEngine = new OptimizationEngine(List.of(new ProcessPriorityAction(identity.pid(), priorities, log)),
                new SessionStateStore(recoveryPath()), log, true);
        OptimizationReport priority = priorityEngine.apply(game.toString());
        if (priority.failed() > 0) throw new IllegalStateException("No se pudo aplicar ABOVE_NORMAL");
        return ConfigurationApplication.valid(combine(safe, priority));
    }

    @Override public synchronized PriorityCaptureMonitor monitorPriorityDuringCapture() {
        if (priorityIdentity == null) throw new IllegalStateException("ABOVE_NORMAL no está activo");
        return new ProcessPriorityCaptureMonitor(priorities, priorityIdentity, log);
    }

    @Override public synchronized PriorityCaptureMonitor monitorExperimentalConfigurationDuringCapture() {
        if (backgroundLoadGuard != null) return backgroundLoadGuard.monitor();
        if (powerThrottlingIdentity != null)
            return new ProcessPowerThrottlingCaptureMonitor(powerThrottling, powerThrottlingIdentity, log);
        return monitorPriorityDuringCapture();
    }

    @Override public synchronized OptimizationReport restore() {
        int applicable = 0, restored = 0, failed = 0; boolean complete = true;
        if (backgroundLoadGuard != null) {
            OptimizationReport value = backgroundLoadGuard.restore();
            applicable += value.applicable(); restored += value.applied(); failed += value.failed(); complete &= value.fullyRestored();
        }
        if (powerThrottlingEngine != null) {
            OptimizationReport value = powerThrottlingEngine.restore();
            applicable += value.applicable(); restored += value.applied(); failed += value.failed(); complete &= value.fullyRestored();
            powerThrottlingEngine = null;
            powerThrottlingIdentity = null;
        }
        if (priorityEngine != null) {
            OptimizationReport value = priorityEngine.restore();
            applicable += value.applicable(); restored += value.applied(); failed += value.failed(); complete &= value.fullyRestored();
            priorityEngine = null;
            priorityIdentity = null;
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
                actions.add(new ProcessPriorityAction(pid, priorities, log));
            }
            if (actions.isEmpty()) return false;
            return new OptimizationEngine(actions, store, log, true).restore().fullyRestored();
        } catch (IOException | RuntimeException exception) {
            log.accept("⚠ No se pudo recuperar prioridad experimental: " + exception.getMessage());
            return false;
        }
    }

    public static boolean recoverIncomplete(ProcessPowerThrottlingService service, Consumer<String> log) {
        SessionStateStore store = new SessionStateStore(highQosRecoveryPath());
        try {
            var state = store.load();
            if (state.isEmpty()) return true;
            List<cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationAction> actions = new ArrayList<>();
            for (var change : state.get().changes()) {
                if (!change.actionId().startsWith(ProcessPowerThrottlingAction.ID_PREFIX)) continue;
                long pid = Long.parseLong(change.actionId().substring(ProcessPowerThrottlingAction.ID_PREFIX.length()));
                actions.add(new ProcessPowerThrottlingAction(pid, service, log));
            }
            if (actions.isEmpty()) return false;
            return new OptimizationEngine(actions, store, log, true).restore().fullyRestored();
        } catch (IOException | RuntimeException exception) {
            log.accept("No se pudo recuperar HighQoS experimental: " + exception.getMessage());
            return false;
        }
    }

    private static OptimizationReport combine(OptimizationReport safe, OptimizationReport experimental) {
        return new OptimizationReport(safe.applicable() + experimental.applicable(), safe.applied() + experimental.applied(),
                safe.failed() + experimental.failed(), false);
    }

    private static Path recoveryPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return base.resolve("OxmanGameOptimizer").resolve("priority-experiment-state.json");
    }
    private static Path highQosRecoveryPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return base.resolve("OxmanGameOptimizer").resolve("high-qos-experiment-state.json");
    }
}
