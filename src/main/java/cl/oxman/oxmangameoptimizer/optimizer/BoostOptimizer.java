package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;
import cl.oxman.oxmangameoptimizer.optimizer.action.PowerPlanAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.ServiceStopAction;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import cl.oxman.oxmangameoptimizer.system.WindowsCommandRunner;
import cl.oxman.oxmangameoptimizer.optimizer.assessment.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BoostOptimizer {

    private static final WindowsCommandRunner COMMANDS = new WindowsCommandRunner();
    private static final OptimizationEngine ENGINE = new OptimizationEngine(List.of(
            new PowerPlanAction(COMMANDS),
            // DiagTrack is optional and reversible. WSearch is intentionally preserved: its
            // gaming benefit is marginal and stopping it degrades Windows search/indexing.
            new ServiceStopAction("DiagTrack", "Telemetría de diagnóstico durante la sesión", COMMANDS)
    ), SessionStateStore.localAppData(), LogManager::addLog);
    private static final SystemOptimizationAssessmentService ASSESSMENT =
            new SystemOptimizationAssessmentService(COMMANDS, BoostOptimizer::backgroundCandidateCount);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "optimization-engine");
        thread.setDaemon(true);
        return thread;
    });

    private BoostOptimizer() {
    }

    public static OptimizationReport applyBoost(String gameName) {
        LogManager.addClientLog("Preparando " + gameName + "...");
        LogManager.addClientLog("Analizando configuración del sistema...");
        SystemOptimizationAssessment assessment = ASSESSMENT.assess();
        OptimizationPlan plan = OptimizationPlan.from(assessment);
        assessment.items().forEach(item -> LogManager.addLog("Assessment · " + item.label() + " → "
                + item.status() + " · " + item.detail()));
        assessment.items().stream().filter(item -> item.actionId().equals("background-load"))
                .filter(item -> item.status() == AssessmentStatus.ACTION_AVAILABLE)
                .findFirst().ifPresent(item -> LogManager.addClientLog(
                        "Carga de fondo optimizable detectada. Disponible en herramientas avanzadas."));
        LogManager.addClientLog("Aplicando optimizaciones seguras...");
        OptimizationReport report = ENGINE.apply(gameName, plan.actionIds());
        LogManager.addLog("Optimizaciones: " + report.applied() + "/" + report.applicable() + " aplicadas");
        if (report.failed() > 0) {
            LogManager.addClientLog("No se pudo completar la optimización. Revisa el diagnóstico avanzado.");
        } else if (report.applied() == 0) {
            LogManager.addClientLog("Sistema ya optimizado. No fue necesario realizar cambios.");
        } else {
            LogManager.addClientLog(report.applied() + (report.applied() == 1
                    ? " optimización segura aplicada." : " optimizaciones seguras aplicadas."));
        }
        LogManager.addClientLog("Sistema preparado para jugar. Todos los cambios son reversibles.");
        return report;
    }

    public static CompletableFuture<OptimizationReport> applyBoostAsync(String gameName) {
        return CompletableFuture.supplyAsync(() -> applyBoost(gameName), EXECUTOR);
    }

    public static boolean restoreDefaults() {
        LogManager.addLog("Restaurando Windows");
        OptimizationReport report = ENGINE.restore();
        if (report.fullyRestored()) LogManager.addLog("Sistema restaurado correctamente");
        else LogManager.addLog("⚠ Restauración incompleta; se conservará el snapshot para reintentar");
        return report.fullyRestored();
    }

    public static CompletableFuture<OptimizationReport> restoreAsync() {
        return CompletableFuture.supplyAsync(() -> {
            LogManager.addLog("Restaurando Windows");
            OptimizationReport report = ENGINE.restore();
            if (report.fullyRestored()) LogManager.addLog("Sistema restaurado correctamente");
            else LogManager.addLog("⚠ Restauración incompleta; se conservará el snapshot para reintentar");
            return report;
        }, EXECUTOR);
    }

    public static boolean recoverIncompleteSession() {
        if (!ENGINE.hasIncompleteSession()) return true;
        LogManager.addLog("Se detectó una sesión incompleta; restaurando cambios pendientes");
        return restoreDefaults();
    }

    private static int backgroundCandidateCount() {
        var observations = new cl.oxman.oxmangameoptimizer.performance.benchmark.WindowsBackgroundProcessSource()
                .observe(java.time.Duration.ofMillis(250));
        return new cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessSelector()
                .select(observations, -1, ProcessHandle.current().pid()).size();
    }
}
