package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.io.IOException;

public final class PresentMonCaptureException extends IOException {
    private final Integer exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;

    PresentMonCaptureException(String message, Integer exitCode, String stdout, String stderr, boolean timedOut) {
        super(message);
        this.exitCode = exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.timedOut = timedOut;
    }

    public Integer exitCode() { return exitCode; }
    public String stdout() { return stdout; }
    public String stderr() { return stderr; }
    public boolean timedOut() { return timedOut; }
}
