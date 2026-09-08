package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.action.*;
import cl.oxman.oxmangameoptimizer.performance.benchmark.*;
import cl.oxman.oxmangameoptimizer.system.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static cl.oxman.oxmangameoptimizer.optimizer.assessment.PerformanceOpportunity.*;

public final class SystemOptimizationAssessmentService {
    private static final Pattern GUID = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private final SystemCommandRunner commands;
    private final DiagnosisThresholds thresholds;
    public SystemOptimizationAssessmentService(SystemCommandRunner commands) { this(commands, DiagnosisThresholds.DEFAULT); }
    public SystemOptimizationAssessmentService(SystemCommandRunner commands, DiagnosisThresholds thresholds) {
        this.commands = commands; this.thresholds = thresholds;
    }
    public SystemOptimizationAssessment assess() { return assess(PreGameBaseline.unavailable(), null, null); }

    public SystemOptimizationAssessment assess(PreGameBaseline baseline, ProcessIdentity game,
            ProcessPowerThrottlingService qos) {
        List<OptimizationAssessmentItem> items = new ArrayList<>();
        List<PerformanceOpportunity> opportunities = new ArrayList<>();
        var power = assessPowerPlan();
        items.add(power);
        opportunities.add(new PerformanceOpportunity(power.status() == AssessmentStatus.ACTION_AVAILABLE
                ? "POWER_PLAN_NOT_OPTIMAL" : "POWER_PLAN", Category.POWER,
                power.status() == AssessmentStatus.ACTION_AVAILABLE ? Severity.NOTICE
                        : power.status() == AssessmentStatus.OPTIMIZED ? Severity.OK : Severity.UNKNOWN,
                power.status(), power.detail(), power.status() == AssessmentStatus.ACTION_AVAILABLE ? "Activar High Performance temporalmente" : "Sin cambios",
                EstimatedImpact.UNKNOWN));
        items.add(new OptimizationAssessmentItem("process-priority", "Process Priority", AssessmentStatus.NOT_APPLICABLE,
                OptimizationSafety.EXPERIMENTAL, "Sólo experimento avanzado; nunca se promueve al BOOST."));
        AssessmentStatus gameStatus = AssessmentStatus.NOT_APPLICABLE;
        String gameDetail = "Juego no identificado de forma única; no se modifica su proceso.";
        if (game != null && qos != null) {
            try {
                var state = qos.read(game);
                gameStatus = state.isExecutionSpeedDisabled() ? AssessmentStatus.OPTIMIZED : AssessmentStatus.NOT_APPLICABLE;
                gameDetail = game.processName() + " PID " + game.pid() + ": " + (state.isExecutionSpeedDisabled()
                        ? "HighQoS ya activo; NO_CHANGE." : "HighQoS no explícito; permanece experimental.");
            } catch (RuntimeException exception) { gameStatus = AssessmentStatus.UNSUPPORTED; gameDetail = "No se pudo consultar HighQoS; sin cambios."; }
        }
        items.add(new OptimizationAssessmentItem("high-qos", "HighQoS", gameStatus, OptimizationSafety.EXPERIMENTAL, gameDetail));
        opportunities.add(new PerformanceOpportunity("GAME_CONFIGURATION", Category.GAME,
                gameStatus == AssessmentStatus.OPTIMIZED ? Severity.OK : Severity.UNKNOWN, gameStatus, gameDetail, "Sin cambios automáticos", EstimatedImpact.UNKNOWN));
        Severity memory = thresholds.memorySeverity(baseline.memoryUsedPercent());
        opportunities.add(new PerformanceOpportunity("MEMORY_PRESSURE", Category.RAM, memory, AssessmentStatus.NOT_APPLICABLE,
                memory == Severity.HIGH_PRESSURE ? "Presión de memoria elevada" : memory == Severity.NOTICE ? "Uso de memoria elevado"
                        : memory == Severity.OK ? "Memoria disponible suficiente" : "Memoria: lectura no disponible",
                memory == Severity.HIGH_PRESSURE || memory == Severity.NOTICE ? "Revisar aplicaciones abiertas manualmente" : "Sin cambios", EstimatedImpact.UNKNOWN));
        long gamePid = game == null ? -1 : game.pid();
        double backgroundCpu = baseline.backgroundCpu(gamePid, ProcessHandle.current().pid());
        boolean highBackground = baseline.complete() && backgroundCpu >= thresholds.backgroundNoticePercent();
        opportunities.add(new PerformanceOpportunity("HIGH_BACKGROUND_CPU", Category.CPU,
                !baseline.complete() ? Severity.UNKNOWN : highBackground ? Severity.NOTICE : Severity.OK,
                AssessmentStatus.NOT_APPLICABLE, !baseline.complete() ? "Baseline no disponible"
                        : highBackground ? "Alta actividad CPU en segundo plano observada" : "Actividad de fondo observada baja",
                "No identifica cuellos de botella ni mide FPS", EstimatedImpact.UNKNOWN));
        List<BackgroundProcessObservation> selected = baseline.complete()
                ? new BackgroundProcessSelector().select(baseline.processes(), gamePid, ProcessHandle.current().pid()) : List.of();
        List<BackgroundProcessObservation> needed = new ArrayList<>();
        for (var candidate : selected) {
            if (qos == null) continue;
            try {
                var state = qos.read(candidate.identity());
                if (state.isExecutionSpeedEnabled()) {
                    items.add(new OptimizationAssessmentItem(BackgroundEcoQosAction.ID_PREFIX + candidate.identity().pid(),
                            "Carga de fondo", AssessmentStatus.OPTIMIZED, OptimizationSafety.SAFE, "EcoQoS ya activo; NO_CHANGE."));
                } else needed.add(candidate);
            } catch (RuntimeException exception) { /* Unknown/access denied: do nothing. */ }
        }
        double aggregate = needed.stream().mapToDouble(BackgroundProcessObservation::cpuAveragePercent).sum();
        boolean actionable = aggregate >= thresholds.candidateAggregatePercent();
        if (actionable) for (var candidate : needed) {
            items.add(new OptimizationAssessmentItem(BackgroundEcoQosAction.ID_PREFIX + candidate.identity().pid(),
                    "Carga de fondo", AssessmentStatus.ACTION_AVAILABLE, OptimizationSafety.SAFE,
                    candidate.identity().processName() + ": EcoQoS temporal con snapshot y verificación"));
        }
        opportunities.add(new PerformanceOpportunity("BACKGROUND_PROCESSES_AVAILABLE", Category.BACKGROUND,
                !baseline.complete() ? Severity.UNKNOWN : actionable ? Severity.NOTICE : Severity.OK,
                actionable ? AssessmentStatus.ACTION_AVAILABLE : AssessmentStatus.NOT_APPLICABLE,
                actionable ? "Carga de fondo elegible para ajuste reversible" : "Sin carga elegible suficiente para intervenir",
                actionable ? "Background Load Guard" : "NO_CHANGE", EstimatedImpact.UNKNOWN));
        boolean available = items.stream().anyMatch(i -> i.safety() == OptimizationSafety.SAFE && i.status() == AssessmentStatus.ACTION_AVAILABLE);
        boolean knownReady = baseline.complete() && power.status() == AssessmentStatus.OPTIMIZED
                && memory == Severity.OK && !highBackground;
        opportunities.add(new PerformanceOpportunity(available ? "SAFE_ACTIONS_AVAILABLE" : knownReady ? "ALREADY_OPTIMIZED" : "NO_SAFE_ACTION_AVAILABLE",
                Category.SYSTEM, available ? Severity.NOTICE : knownReady ? Severity.OK : Severity.UNKNOWN,
                available ? AssessmentStatus.ACTION_AVAILABLE : knownReady ? AssessmentStatus.OPTIMIZED : AssessmentStatus.NOT_APPLICABLE,
                available ? "Optimizaciones seguras disponibles" : knownReady ? "No se encontraron optimizaciones seguras necesarias"
                        : "No hay acciones seguras disponibles; revisar el diagnóstico", "Sin promesas de FPS", EstimatedImpact.UNKNOWN));
        return new SystemOptimizationAssessment(items, baseline, opportunities, selected);
    }

    private OptimizationAssessmentItem assessPowerPlan() {
        try {
            var result = commands.run(List.of("powercfg", "/getactivescheme"), Duration.ofSeconds(5));
            var matcher = GUID.matcher(result.output());
            if (!result.succeeded() || !matcher.find()) return power(AssessmentStatus.UNSUPPORTED, "Plan de energía no disponible");
            String guid = matcher.group();
            if (PowerPlanPolicy.optimized(guid)) return power(AssessmentStatus.OPTIMIZED, "Plan de energía optimizado (" + guid + ")");
            if (!PowerPlanPolicy.changeable(guid)) return power(AssessmentStatus.NOT_APPLICABLE, "Plan personalizado: se conserva (" + guid + ")");
            var listed = commands.run(List.of("powercfg", "/list"), Duration.ofSeconds(5));
            if (!listed.succeeded() || !listed.output().toLowerCase(Locale.ROOT).contains(PowerPlanPolicy.HIGH))
                return power(AssessmentStatus.UNSUPPORTED, "High Performance no está disponible; se conserva el plan " + guid);
            return power(AssessmentStatus.ACTION_AVAILABLE, "Plan " + guid + ": High Performance disponible temporalmente");
        } catch (RuntimeException exception) { return power(AssessmentStatus.UNSUPPORTED, "Plan de energía no disponible"); }
    }
    private static OptimizationAssessmentItem power(AssessmentStatus status, String detail) {
        return new OptimizationAssessmentItem(PowerPlanAction.ID, "Plan de energía", status, OptimizationSafety.SAFE, detail);
    }
}
