package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;

public final class ClientSessionStatus {
    private ClientSessionStatus() { }

    public static String afterOptimization(OptimizationReport report) {
        if (report.failed() > 0) return "ATENCIÓN REQUERIDA";
        return report.applied() > 0 ? "OPTIMIZACIÓN ACTIVA" : "SISTEMA LISTO";
    }

    public static String starting(String game) { return "SISTEMA LISTO · Iniciando " + game + "..."; }

    public static String running(String game) { return "LISTO PARA JUGAR · " + game; }
}
