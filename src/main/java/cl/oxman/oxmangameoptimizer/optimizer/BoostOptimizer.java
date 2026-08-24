package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;
import cl.oxman.oxmangameoptimizer.optimizer.action.PowerPlanAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.ServiceStopAction;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import cl.oxman.oxmangameoptimizer.system.WindowsCommandRunner;

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
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "optimization-engine");
        thread.setDaemon(true);
        return thread;
    });

    private BoostOptimizer() {
    }

    public static OptimizationReport applyBoost(String gameName) {
        LogManager.addLog("Preparando perfil competitivo para " + gameName);
        OptimizationReport report = ENGINE.apply(gameName);
        LogManager.addLog("Optimizaciones: " + report.applied() + "/" + report.applicable() + " aplicadas");
        LogManager.addLog("Competitive Mode activo. Todos los cambios aplicados son reversibles.");
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
}
