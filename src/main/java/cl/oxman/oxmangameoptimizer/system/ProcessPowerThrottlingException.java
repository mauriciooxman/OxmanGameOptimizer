package cl.oxman.oxmangameoptimizer.system;

public final class ProcessPowerThrottlingException extends RuntimeException {
    public enum Reason { INVALID_PID, ACCESS_DENIED, PROCESS_ENDED, PID_REUSED, NATIVE_FAILURE }
    private final Reason reason;
    public ProcessPowerThrottlingException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
