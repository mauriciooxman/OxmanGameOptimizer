package cl.oxman.oxmangameoptimizer.optimizer.action;

public record ActionOutcome(String actionId, Status status, String detail) {
    public enum Status { APPLIED, ALREADY_OPTIMIZED, NOT_APPLICABLE, SKIPPED, FAILED }
}
