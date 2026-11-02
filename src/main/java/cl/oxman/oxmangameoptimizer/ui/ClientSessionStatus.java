package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome;
import cl.oxman.oxmangameoptimizer.optimizer.action.ProcessPriorityAction;

public final class ClientSessionStatus {
    private ClientSessionStatus() { }

    public static String afterOptimization(OptimizationReport report) {
        if (report.failed() > 0) return "ATENCIÓN REQUERIDA";
        return report.applied() > 0 ? "OPTIMIZACIÓN ACTIVA" : "SISTEMA LISTO";
    }

    public static String starting(String game) { return "SISTEMA LISTO · Iniciando " + game + "..."; }

    public static String running(String game) { return "LISTO PARA JUGAR · " + game; }

    public static String running(String game, OptimizationReport priorityReport) {
        String priority = priorityReport.outcomes().stream()
                .filter(outcome -> outcome.actionId().startsWith(ProcessPriorityAction.HIGH_ID_PREFIX))
                .findFirst().map(ClientSessionStatus::priorityStatus).orElse("Prioridad HIGH: no disponible");
        return running(game) + " · " + priority;
    }

    private static String priorityStatus(ActionOutcome outcome) {
        return switch (outcome.status()) {
            case APPLIED -> "Prioridad HIGH: aplicada";
            case ALREADY_OPTIMIZED -> "Prioridad HIGH: ya activa";
            case NOT_APPLICABLE, SKIPPED -> "Prioridad HIGH: no disponible";
            case FAILED -> "Prioridad HIGH: error";
        };
    }
}
