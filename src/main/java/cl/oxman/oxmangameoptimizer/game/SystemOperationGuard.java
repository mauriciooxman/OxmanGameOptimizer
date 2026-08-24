package cl.oxman.oxmangameoptimizer.game;

import java.util.concurrent.atomic.AtomicReference;

public final class SystemOperationGuard {
    public enum Operation { IDLE, GAMING_SESSION, BENCHMARK, RESTORE }
    private static final AtomicReference<Operation> ACTIVE = new AtomicReference<>(Operation.IDLE);
    private SystemOperationGuard() { }
    public static boolean acquire(Operation operation) { return ACTIVE.compareAndSet(Operation.IDLE, operation); }
    public static void release(Operation operation) { ACTIVE.compareAndSet(operation, Operation.IDLE); }
    public static Operation active() { return ACTIVE.get(); }
}
