package cl.oxman.oxmangameoptimizer.system;

import java.time.Duration;

public interface ManagedProcess {
    boolean waitFor(Duration timeout) throws InterruptedException;
    int exitCode();
    String stdout() throws InterruptedException;
    String stderr() throws InterruptedException;
    void requestStop();
    void forceStop();
}
