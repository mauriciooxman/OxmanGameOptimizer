package cl.oxman.oxmangameoptimizer.system;

import java.util.Objects;

/** Stable identity used to reject a recycled Windows PID. */
public record ProcessIdentity(long pid, long startEpochMillis, String processName) {
    public ProcessIdentity {
        if (pid <= 0) throw new IllegalArgumentException("PID must be positive");
        if (startEpochMillis <= 0) throw new IllegalArgumentException("Process start time is required");
        processName = Objects.requireNonNull(processName);
    }
}
