package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.optimizer.*;
import cl.oxman.oxmangameoptimizer.optimizer.assessment.*;
import cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome;
import java.util.Locale;
import java.util.stream.Collectors;

/** Pure presentation: no native data leaks into the client summary. */
public final class DiagnosisPresentation {
    private DiagnosisPresentation() { }
    public static String client(SystemOptimizationAssessment assessment) {
        var memory = assessment.opportunities().stream().filter(o -> o.category() == PerformanceOpportunity.Category.RAM)
                .map(PerformanceOpportunity::description).findFirst().orElse("Memoria: no disponible");
        var power = assessment.items().stream().filter(i -> i.actionId().equals("power-plan")).findFirst();
        String powerText = power.map(i -> switch (i.status()) {
            case OPTIMIZED -> "Plan de energía optimizado";
            case ACTION_AVAILABLE -> "Ajuste de energía disponible";
            default -> "Plan de energía conservado; sin ajuste automático";
        }).orElse("Plan de energía: no disponible");
        var background = assessment.opportunities().stream().filter(o -> o.category() == PerformanceOpportunity.Category.CPU)
                .map(PerformanceOpportunity::description).findFirst().orElse("Carga de fondo: no disponible");
        int count = AdaptiveOptimizationPlan.from(assessment).actionIds().size();
        String game = assessment.items().stream().filter(i -> i.actionId().equals("high-qos"))
                .map(i -> i.status() == AssessmentStatus.OPTIMIZED ? "Juego: configuración energética correcta"
                        : "Juego: sin ajuste automático de proceso").findFirst().orElse("Juego: no identificado");
        return "ANÁLISIS DEL SISTEMA\n" + powerText + "\n" + memory + "\n" + background + "\n"
                + game + "\n" + CountText.format(count, "optimización segura disponible", "optimizaciones seguras disponibles");
    }
    public static boolean needsAttention(SystemOptimizationAssessment assessment) {
        return assessment.opportunities().stream().anyMatch(o -> o.severity() == PerformanceOpportunity.Severity.HIGH_PRESSURE
                || (o.id().equals("HIGH_BACKGROUND_CPU") && o.severity() == PerformanceOpportunity.Severity.NOTICE))
                || !assessment.baseline().complete()
                || assessment.items().stream().anyMatch(i -> i.actionId().equals("power-plan")
                        && (i.status() == AssessmentStatus.UNSUPPORTED || i.status() == AssessmentStatus.NOT_APPLICABLE));
    }
    public static String summary(OptimizationReport report, SystemOptimizationAssessment assessment) {
        if (report.failed() > 0) return "ATENCIÓN REQUERIDA\n"
                + CountText.format(report.applied(), "cambio aplicado", "cambios aplicados")
                + ". Algunas acciones fallaron; revisa el diagnóstico avanzado. Puedes restaurar los cambios.";
        String changes = report.applied() == 0
                ? (AdaptiveOptimizationPlan.from(assessment).actionIds().isEmpty()
                    ? "No se encontraron optimizaciones seguras necesarias. 0 cambios aplicados."
                    : "0 cambios aplicados: los ajustes previstos ya no eran necesarios o aplicables.")
                : CountText.format(report.applied(), "cambio aplicado y verificado. Es reversible.",
                        "cambios aplicados y verificados. Todos son reversibles.");
        String details = report.outcomes().stream().filter(o -> o.status() == ActionOutcome.Status.APPLIED)
                .map(o -> o.actionId().equals("power-plan") ? "Plan de energía ajustado"
                        : o.actionId().startsWith("background-ecoqos:") ? "Política energética de fondo ajustada" : "Ajuste seguro aplicado")
                .distinct().collect(Collectors.joining("\n"));
        if (!details.isBlank()) changes += "\n" + details;
        if (needsAttention(assessment)) return "REVISAR CARGA DEL SISTEMA\n" + changes
                + "\nEl diagnóstico inicial requiere atención; no se ha medido una mejora de rendimiento.";
        return (report.applied() == 0 ? "SISTEMA LISTO\n" : "OPTIMIZACIÓN COMPLETADA\n") + changes
                + "\nSistema preparado para jugar.";
    }
    public static String advanced(SystemOptimizationAssessment assessment) {
        var b = assessment.baseline();
        return String.format(Locale.ROOT, "DIAGNÓSTICO DETALLADO\nBaseline: %s · completo: %s\nCPU promedio: %.2f%%\nRAM máxima: %.2f%% · disponible mínima: %.2f GiB\nProcesos: %d\n",
                CountText.format(b.sampleCount(), "muestra", "muestras"), b.complete(), b.cpuAveragePercent(), b.memoryUsedPercent(), b.availableMemoryBytes() / 1073741824.0, b.processCount())
                + assessment.items().stream().map(i -> i.label() + ": " + i.status() + " · " + i.safety() + " · " + i.detail()).collect(Collectors.joining("\n"))
                + "\nCandidatos seguros:\n" + assessment.candidates().stream().map(p -> String.format(Locale.ROOT,
                        "%s PID %d CPU promedio %.2f%%", p.identity().processName(), p.identity().pid(), p.cpuAveragePercent())).collect(Collectors.joining("\n"))
                + "\nAcciones previstas: " + AdaptiveOptimizationPlan.from(assessment).actionIds()
                + "\nProcesos observados con mayor actividad CPU:\n" + b.processes().stream()
                        .sorted(java.util.Comparator.comparingDouble(cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessObservation::cpuAveragePercent).reversed())
                        .limit(5).map(p -> String.format(Locale.ROOT, "%s PID %d %.2f%%", p.identity().processName(), p.identity().pid(), p.cpuAveragePercent()))
                        .collect(Collectors.joining("\n"))
                + String.format(Locale.ROOT, "\nCPU agregada de candidatos: %.2f%%", assessment.candidates().stream()
                        .mapToDouble(cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessObservation::cpuAveragePercent).sum())
                + "\nCobertura de procesos limitada a lecturas disponibles. Este diagnóstico no es un benchmark FPS.";
    }
    public static String outcomes(OptimizationReport report) {
        return report.outcomes().stream().map(o -> o.actionId() + ": " + o.status() + " · " + o.detail()).collect(Collectors.joining("\n"));
    }
}
