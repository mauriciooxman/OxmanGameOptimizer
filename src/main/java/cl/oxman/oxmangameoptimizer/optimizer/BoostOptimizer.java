package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.*;
import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.action.*;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import cl.oxman.oxmangameoptimizer.system.*;
import cl.oxman.oxmangameoptimizer.optimizer.assessment.*;
import cl.oxman.oxmangameoptimizer.performance.benchmark.WindowsBackgroundProcessSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public final class BoostOptimizer {
    private static final WindowsCommandRunner COMMANDS = new WindowsCommandRunner();
    private static final ProcessPowerThrottlingService QOS = new WindowsProcessPowerThrottlingService();
    private static final ProcessPriorityController PRIORITIES = new WindowsProcessPriorityService();
    private static final DiagnosisThresholds THRESHOLDS = DiagnosisThresholds.DEFAULT;
    private static final SessionStateStore STORE = SessionStateStore.localAppData();
    private static OptimizationEngine engine;
    private static volatile BiConsumer<SystemOptimizationAssessment, OptimizationReport> diagnosisListener = (a, r) -> { };
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "optimization-engine"); thread.setDaemon(true); return thread;
    });
    private BoostOptimizer() { }
    public static void setDiagnosisListener(BiConsumer<SystemOptimizationAssessment, OptimizationReport> listener) {
        diagnosisListener = Objects.requireNonNull(listener);
    }
    public static synchronized OptimizationReport applyBoost(String gameName) { return applyBoost(gameName, null); }
    public static synchronized OptimizationReport applyBoost(GameProfile game) {
        return applyBoost(game.toString(), game.findRunningProcessIdentity().orElse(null));
    }
    private static OptimizationReport applyBoost(String gameName, ProcessIdentity game) {
        if (!recoverIncompleteSession()) return new OptimizationReport(0, 0, 1, false);
        LogManager.addClientLog("Analizando sistema... (aproximadamente 4 segundos)");
        PreGameBaseline baseline;
        try { baseline = PreGameBaselineService.windows(THRESHOLDS).collect(); }
        catch (RuntimeException | LinkageError exception) { baseline = PreGameBaseline.unavailable(); }
        var assessment = new SystemOptimizationAssessmentService(COMMANDS, THRESHOLDS).assess(baseline, game, QOS);
        var plan = AdaptiveOptimizationPlan.from(assessment);
        LogManager.addClientLog(DiagnosisPresentation.client(assessment));
        LogManager.addLog(DiagnosisPresentation.advanced(assessment));
        diagnosisListener.accept(assessment, null);
        List<OptimizationAction> actions = new ArrayList<>();
        actions.add(new PowerPlanAction(COMMANDS));
        actions.addAll(AdaptiveBackgroundAction.create(assessment, plan, THRESHOLDS, QOS,
                new WindowsBackgroundProcessSource(), game == null ? -1 : game.pid(), LogManager::addLog));
        engine = new OptimizationEngine(actions, STORE, LogManager::addLog);
        if (!plan.actionIds().isEmpty()) LogManager.addClientLog("Aplicando ajustes seguros y verificando sus estados...");
        OptimizationReport executed = engine.apply(gameName, plan.actionIds());
        List<ActionOutcome> outcomes = new ArrayList<>(executed.outcomes());
        assessment.items().stream().filter(i -> !plan.actionIds().contains(i.actionId())).forEach(i -> outcomes.add(new ActionOutcome(
                i.actionId(), switch (i.status()) {
                    case OPTIMIZED -> ActionOutcome.Status.ALREADY_OPTIMIZED;
                    case NOT_APPLICABLE, UNSUPPORTED -> ActionOutcome.Status.NOT_APPLICABLE;
                    case ACTION_AVAILABLE -> ActionOutcome.Status.SKIPPED;
                }, i.detail())));
        var report = new OptimizationReport(executed.applicable(), executed.applied(), executed.failed(), executed.fullyRestored(), outcomes);
        report.outcomes().stream().filter(outcome -> PowerPlanAction.ID.equals(outcome.actionId()))
                .findFirst().ifPresent(outcome -> LogManager.addClientLog(switch (outcome.status()) {
                    case APPLIED -> "Plan Alto rendimiento: aplicado y verificado.";
                    case ALREADY_OPTIMIZED -> "Plan Alto rendimiento o equivalente: ya activo.";
                    case NOT_APPLICABLE -> "Plan Alto rendimiento: no disponible; " + outcome.detail();
                    case FAILED -> "Plan Alto rendimiento: error; " + outcome.detail();
                    case SKIPPED -> "Plan Alto rendimiento: omitido; " + outcome.detail();
                }));
        LogManager.addClientLog(DiagnosisPresentation.summary(report, assessment));
        diagnosisListener.accept(assessment, report);
        return report;
    }
    public static CompletableFuture<OptimizationReport> applyBoostAsync(String gameName) {
        return CompletableFuture.supplyAsync(() -> applyBoost(gameName), EXECUTOR);
    }
    public static CompletableFuture<OptimizationReport> applyBoostAsync(GameProfile game) {
        return CompletableFuture.supplyAsync(() -> {
            OptimizationReport base = applyBoost(game);
            if (base.hasCriticalFailure()) return base;
            return combine(base, applyGamePriority(game));
        }, EXECUTOR);
    }

    /** Applies HIGH only after the selected game's exact process identity is available. */
    public static synchronized OptimizationReport applyGamePriority(GameProfile game) {
        var identity = game.findRunningProcessIdentity();
        if (identity.isEmpty()) {
            var outcome = new ActionOutcome(ProcessPriorityAction.HIGH_ID_PREFIX + "pending",
                    ActionOutcome.Status.NOT_APPLICABLE,
                    "Juego todavía no iniciado; prioridad HIGH pendiente de detección");
            LogManager.addLog(outcome.actionId() + " · " + outcome.status() + " · " + outcome.detail());
            return new OptimizationReport(0, 0, 0, false, List.of(outcome));
        }
        if (engine == null) {
            var outcome = new ActionOutcome("session", ActionOutcome.Status.FAILED,
                    "No existe una sesión BOOST activa para registrar la prioridad");
            return new OptimizationReport(0, 0, 1, false, List.of(outcome));
        }
        ProcessPriorityAction action = ProcessPriorityAction.high(identity.get(), PRIORITIES, LogManager::addLog);
        OptimizationReport report = engine.applyAdditional(game.toString(), action);
        report.outcomes().forEach(outcome -> LogManager.addClientLog(switch (outcome.status()) {
            case APPLIED -> "Prioridad HIGH aplicada y verificada para " + identity.get().processName() + ".";
            case ALREADY_OPTIMIZED -> "Prioridad HIGH ya estaba activa para el juego.";
            case NOT_APPLICABLE -> "Prioridad HIGH no disponible: " + outcome.detail();
            case FAILED -> "Prioridad HIGH no aplicada: " + outcome.detail();
            case SKIPPED -> "Prioridad HIGH omitida: " + outcome.detail();
        }));
        return report;
    }
    public static synchronized boolean restoreDefaults() { return restoreReport().fullyRestored(); }
    private static synchronized OptimizationReport restoreReport() {
        try {
            if (engine == null) engine = recoveryEngine();
            var report = engine.restore();
            if (report.fullyRestored()) { engine = null; LogManager.addLog("Sistema restaurado correctamente"); }
            else LogManager.addLog("Restauración pendiente; snapshot conservado para reintentar");
            return report;
        } catch (Exception exception) {
            LogManager.addLog("No se pudo leer la recuperación: " + exception.getMessage());
            return new OptimizationReport(0, 0, 1, false);
        }
    }
    public static CompletableFuture<OptimizationReport> restoreAsync() {
        return CompletableFuture.supplyAsync(BoostOptimizer::restoreReport, EXECUTOR);
    }
    public static synchronized boolean recoverIncompleteSession() {
        try { if (STORE.load().isEmpty()) return true; }
        catch (Exception exception) { return false; }
        return restoreDefaults();
    }
    public static CompletableFuture<Boolean> recoverIncompleteSessionAsync() {
        return CompletableFuture.supplyAsync(BoostOptimizer::recoverIncompleteSession, EXECUTOR);
    }
    private static OptimizationReport combine(OptimizationReport first, OptimizationReport second) {
        List<ActionOutcome> outcomes = new ArrayList<>(first.outcomes());
        outcomes.addAll(second.outcomes());
        return new OptimizationReport(first.applicable() + second.applicable(),
                first.applied() + second.applied(), first.failed() + second.failed(), false, outcomes);
    }
    private static OptimizationEngine recoveryEngine() throws java.io.IOException {
        List<OptimizationAction> actions = new ArrayList<>();
        actions.add(new PowerPlanAction(COMMANDS));
        // Read-only compatibility with v1.1 snapshots; never planned by v1.2.
        actions.add(new ServiceStopAction("DiagTrack", "Telemetría de diagnóstico", COMMANDS));
        var state = STORE.load();
        if (state.isPresent()) for (var change : state.get().changes()) {
            if (change.actionId().startsWith(BackgroundEcoQosAction.ID_PREFIX)) {
                long pid = Long.parseLong(change.actionId().substring(BackgroundEcoQosAction.ID_PREFIX.length()));
                // restore decodes and validates the exact saved identity; no PID-only mutation.
                actions.add(new BackgroundEcoQosAction(new ProcessIdentity(pid, 1, "recovery"), QOS, LogManager::addLog));
            } else if (change.actionId().startsWith(ProcessPriorityAction.HIGH_ID_PREFIX)) {
                long pid = Long.parseLong(change.actionId().substring(ProcessPriorityAction.HIGH_ID_PREFIX.length()));
                actions.add(ProcessPriorityAction.highRecovery(pid, PRIORITIES, LogManager::addLog));
            }
        }
        return new OptimizationEngine(actions, STORE, LogManager::addLog);
    }
}
