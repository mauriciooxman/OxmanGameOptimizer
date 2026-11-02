package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome;
import cl.oxman.oxmangameoptimizer.optimizer.action.PowerPlanAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.ProcessPriorityAction;

import java.util.Optional;

public record OptimizationReport(int applicable, int applied, int failed, boolean fullyRestored,
        java.util.List<cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome> outcomes) {
    public OptimizationReport { outcomes = java.util.List.copyOf(outcomes); }
    public OptimizationReport(int applicable, int applied, int failed, boolean fullyRestored) {
        this(applicable, applied, failed, fullyRestored, java.util.List.of());
    }
    public OptimizationReport(int applicable, int applied, boolean fullyRestored) {
        this(applicable, applied, 0, fullyRestored);
    }
    public int omitted() { return Math.max(0, applicable - applied - failed); }
    public boolean hasCriticalFailure() { return applicable == 0 && failed > 0; }

    public ConfigurationCheck validateBenchmarkConfiguration(boolean requireHighPriority) {
        Optional<ActionOutcome> power = outcomes.stream()
                .filter(outcome -> PowerPlanAction.ID.equals(outcome.actionId())).findFirst();
        Optional<ActionOutcome> high = outcomes.stream()
                .filter(outcome -> outcome.actionId().startsWith(ProcessPriorityAction.HIGH_ID_PREFIX)).findFirst();
        String state = "power-plan=" + status(power) + ", process-priority-high=" + status(high);

        Optional<ActionOutcome> failure = outcomes.stream()
                .filter(outcome -> outcome.status() == ActionOutcome.Status.FAILED).findFirst();
        if (failure.isPresent()) {
            ActionOutcome outcome = failure.get();
            return new ConfigurationCheck(false, state + "; " + outcome.actionId() + ": " + outcome.detail());
        }
        if (failed > 0) return new ConfigurationCheck(false, state + "; el reporte contiene fallos sin detalle");
        if (power.isEmpty()) return new ConfigurationCheck(false, state + "; falta el estado del plan de energía");
        if (power.get().status() == ActionOutcome.Status.SKIPPED)
            return new ConfigurationCheck(false, state + "; el plan de energía requerido fue omitido");
        if (requireHighPriority && (high.isEmpty() || (high.get().status() != ActionOutcome.Status.APPLIED
                && high.get().status() != ActionOutcome.Status.ALREADY_OPTIMIZED))) {
            String detail = high.map(ActionOutcome::detail).orElse("sin resultado de prioridad HIGH");
            return new ConfigurationCheck(false, state + "; " + detail);
        }
        return new ConfigurationCheck(true, state);
    }

    private static String status(Optional<ActionOutcome> outcome) {
        return outcome.map(value -> value.status().name()).orElse("MISSING");
    }

    public record ConfigurationCheck(boolean complete, String detail) { }
}
