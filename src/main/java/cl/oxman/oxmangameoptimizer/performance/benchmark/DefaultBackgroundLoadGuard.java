package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.*;
import cl.oxman.oxmangameoptimizer.optimizer.action.*;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import cl.oxman.oxmangameoptimizer.system.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public final class DefaultBackgroundLoadGuard implements BackgroundLoadGuard {
    static final Duration OBSERVATION_WINDOW = Duration.ofMillis(750);
    private final BackgroundProcessSource source;
    private final BackgroundProcessSelector selector;
    private final ProcessPowerThrottlingService service;
    private final Consumer<String> log;
    private OptimizationEngine engine;
    private List<ProcessIdentity> active = List.of();

    public DefaultBackgroundLoadGuard(BackgroundProcessSource source, BackgroundProcessSelector selector,
            ProcessPowerThrottlingService service, Consumer<String> log) {
        this.source = source; this.selector = selector; this.service = service; this.log = log;
    }

    @Override public synchronized ConfigurationApplication apply(GameProfile game) {
        ProcessIdentity gameIdentity = game.findRunningProcessIdentity().orElseThrow(() ->
                new IllegalStateException("No se pudo identificar un único proceso del juego"));
        return apply(gameIdentity, game.toString());
    }

    synchronized ConfigurationApplication apply(ProcessIdentity gameIdentity, String gameName) {
        log.accept("Background Load Guard");
        List<BackgroundProcessObservation> selected = selector.select(source.observe(OBSERVATION_WINDOW),
                gameIdentity.pid(), ProcessHandle.current().pid());
        log.accept("Eligible processes:");
        selected.forEach(value -> log.accept(String.format(Locale.ROOT, "%s PID %d CPU avg %.1f%%",
                value.identity().processName(), value.identity().pid(), value.cpuAveragePercent())));
        List<BackgroundEcoQosAction> actions = new ArrayList<>();
        List<ProcessIdentity> identities = new ArrayList<>();
        for (BackgroundProcessObservation candidate : selected) {
            try {
                if (service.read(candidate.identity()).isExecutionSpeedEnabled()) continue;
                actions.add(new BackgroundEcoQosAction(candidate.identity(), service, log));
                identities.add(candidate.identity());
            } catch (ProcessPowerThrottlingException exception) {
                log.accept("Excluded " + candidate.identity().processName() + ": " + exception.getMessage());
            }
        }
        if (actions.isEmpty()) {
            log.accept("EcoQoS applied: 0 processes");
            return new ConfigurationApplication(new OptimizationReport(0, 0, 0, true), ConfigurationValidity.NO_CHANGE);
        }
        engine = new OptimizationEngine(new ArrayList<>(actions), new SessionStateStore(recoveryPath()), log, true);
        OptimizationReport report = engine.apply(gameName);
        if (report.failed() > 0 || report.applied() != actions.size()) {
            engine.restore(); engine = null; active = List.of();
            throw new IllegalStateException("No se pudo aplicar Background EcoQoS de forma completa");
        }
        active = List.copyOf(identities);
        log.accept("EcoQoS applied: " + report.applied() + " processes");
        return ConfigurationApplication.valid(report);
    }

    @Override public synchronized PriorityCaptureMonitor monitor() {
        if (active.isEmpty()) throw new IllegalStateException("Background EcoQoS no está activo");
        return new BackgroundEcoQosCaptureMonitor(service, active, log);
    }

    @Override public synchronized OptimizationReport restore() {
        if (engine == null) return new OptimizationReport(0, 0, true);
        OptimizationReport report = engine.restore(); engine = null; active = List.of(); return report;
    }

    public static boolean recoverIncomplete(ProcessPowerThrottlingService service, Consumer<String> log) {
        SessionStateStore store = new SessionStateStore(recoveryPath());
        try {
            var state = store.load();
            if (state.isEmpty()) return true;
            List<OptimizationAction> actions = new ArrayList<>();
            for (var change : state.get().changes()) {
                if (!change.actionId().startsWith(BackgroundEcoQosAction.ID_PREFIX)) continue;
                long pid = Long.parseLong(change.actionId().substring(BackgroundEcoQosAction.ID_PREFIX.length()));
                ProcessIdentity identity;
                try { identity = service.identify(pid); }
                catch (RuntimeException exception) { identity = new ProcessIdentity(pid, -1, "ended"); }
                actions.add(new BackgroundEcoQosAction(identity, service, log));
            }
            if (actions.isEmpty()) return false;
            return new OptimizationEngine(actions, store, log, true).restore().fullyRestored();
        } catch (IOException | RuntimeException exception) {
            log.accept("No se pudo recuperar Background EcoQoS: " + exception.getMessage()); return false;
        }
    }

    private static Path recoveryPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return base.resolve("OxmanGameOptimizer").resolve("background-ecoqos-experiment-state.json");
    }
}
