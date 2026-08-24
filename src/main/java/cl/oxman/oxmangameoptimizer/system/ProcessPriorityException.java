package cl.oxman.oxmangameoptimizer.system;

public final class ProcessPriorityException extends RuntimeException {
    public enum Reason { INVALID_PID, PROCESS_ENDED, PID_REUSED, ACCESS_DENIED, NATIVE_FAILURE }
    private final Reason reason;
    public ProcessPriorityException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
