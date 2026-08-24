package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety;
import cl.oxman.oxmangameoptimizer.optimizer.action.PowerPlanAction;
import cl.oxman.oxmangameoptimizer.system.CommandResult;
import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.function.IntSupplier;

public final class SystemOptimizationAssessmentService {
    private static final String HIGH_PERFORMANCE = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";
    private static final Pattern GUID = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Pattern SERVICE_STATE = Pattern.compile("STATE\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private final SystemCommandRunner commands;
    private final IntSupplier backgroundCandidateCount;

    public SystemOptimizationAssessmentService(SystemCommandRunner commands) { this(commands, () -> 0); }
    public SystemOptimizationAssessmentService(SystemCommandRunner commands, IntSupplier backgroundCandidateCount) {
        this.commands = commands;
        this.backgroundCandidateCount = backgroundCandidateCount;
    }

    public SystemOptimizationAssessment assess() {
        return new SystemOptimizationAssessment(List.of(
                assessPowerPlan(), assessDiagTrack(),
                new OptimizationAssessmentItem("process-priority", "Process Priority",
                        AssessmentStatus.NOT_APPLICABLE, OptimizationSafety.EXPERIMENTAL,
                        "Disponible sólo en Modo Avanzado; no se promociona al BOOST."),
                new OptimizationAssessmentItem("high-qos", "HighQoS",
                        AssessmentStatus.NOT_APPLICABLE, OptimizationSafety.EXPERIMENTAL,
                        "La detección redundante se conserva en el experimento HighQoS."),
                assessBackgroundLoad()));
    }

    private OptimizationAssessmentItem assessPowerPlan() {
        CommandResult result = commands.run(List.of("powercfg", "/getactivescheme"), Duration.ofSeconds(5));
        if (!result.succeeded()) return item(PowerPlanAction.ID, "Plan de energía", AssessmentStatus.UNSUPPORTED,
                "Windows no informó el plan activo.");
        var matcher = GUID.matcher(result.output());
        if (!matcher.find()) return item(PowerPlanAction.ID, "Plan de energía", AssessmentStatus.UNSUPPORTED,
                "No se reconoció el plan activo.");
        boolean optimized = HIGH_PERFORMANCE.equalsIgnoreCase(matcher.group());
        return item(PowerPlanAction.ID, "Plan de energía",
                optimized ? AssessmentStatus.OPTIMIZED : AssessmentStatus.ACTION_AVAILABLE,
                optimized ? "High Performance ya está activo." : "High Performance puede activarse temporalmente.");
    }

    private OptimizationAssessmentItem assessDiagTrack() {
        CommandResult result = commands.run(List.of("sc", "query", "DiagTrack"), Duration.ofSeconds(5));
        if (!result.succeeded()) return item("service:DiagTrack", "Telemetría de diagnóstico",
                AssessmentStatus.NOT_APPLICABLE, "DiagTrack no está disponible.");
        var matcher = SERVICE_STATE.matcher(result.output().toUpperCase(Locale.ROOT));
        if (!matcher.find()) return item("service:DiagTrack", "Telemetría de diagnóstico",
                AssessmentStatus.UNSUPPORTED, "No se pudo determinar su estado.");
        boolean stopped = Integer.parseInt(matcher.group(1)) == 1;
        return item("service:DiagTrack", "Telemetría de diagnóstico",
                stopped ? AssessmentStatus.OPTIMIZED : AssessmentStatus.ACTION_AVAILABLE,
                stopped ? "DiagTrack ya está detenido." : "Puede detenerse durante la sesión.");
    }

    private OptimizationAssessmentItem assessBackgroundLoad() {
        int count;
        try { count = Math.max(0, backgroundCandidateCount.getAsInt()); }
        catch (RuntimeException exception) { count = 0; }
        return new OptimizationAssessmentItem("background-load", "Carga de fondo",
                count > 0 ? AssessmentStatus.ACTION_AVAILABLE : AssessmentStatus.OPTIMIZED,
                OptimizationSafety.EXPERIMENTAL, count > 0
                ? count + " procesos elegibles detectados; permanece experimental."
                : "No se detectó carga elegible que requiera cambios.");
    }

    private static OptimizationAssessmentItem item(String id, String label, AssessmentStatus status, String detail) {
        return new OptimizationAssessmentItem(id, label, status, OptimizationSafety.SAFE, detail);
    }
}
