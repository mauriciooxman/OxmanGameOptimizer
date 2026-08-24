package cl.oxman.oxmangameoptimizer.system;

public record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    public CommandResult(int exitCode, String combinedOutput, boolean timedOut) {
        this(exitCode, combinedOutput, "", timedOut);
    }
    public String output() { return stdout + stderr; }
    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
