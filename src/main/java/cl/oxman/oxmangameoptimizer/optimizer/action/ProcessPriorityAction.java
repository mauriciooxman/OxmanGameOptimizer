package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.ProcessPriorityController;
import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import cl.oxman.oxmangameoptimizer.system.ProcessPriority;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityException;

/** Experimental only; never included in the default Competitive Mode action list. */
public final class ProcessPriorityAction implements OptimizationAction {
    private static final ProcessPriority TARGET = ProcessPriority.ABOVE_NORMAL;
    private final long processId;
    private final ProcessPriorityController priorities;
    private String originalState;
    private ProcessIdentity identity;
    public ProcessPriorityAction(long processId, ProcessPriorityController priorities) {
        this.processId = processId; this.priorities = priorities;
    }
    public String id() { return "process-priority:" + processId; }
    public String name() { return "Game Process Priority"; }
    public String description() { return "Prueba Above Normal para el proceso real del juego"; }
    public boolean isSupported() {
        try { identity = priorities.identify(processId); priorities.read(identity); return true; }
        catch (ProcessPriorityException exception) { return false; }
    }
    public boolean requiresAdministrator() { return false; }
    public boolean isReversible() { return true; }
    public OptimizationSafety safety() { return OptimizationSafety.EXPERIMENTAL; }
    public String originalState() { return originalState; }
    public OptimizationResult apply() {
        try {
            identity = priorities.identify(processId);
            ProcessPriority original = priorities.read(identity);
            originalState = encode(identity, original);
            if (original == TARGET) return OptimizationResult.unchanged("ya estaba en ABOVE_NORMAL");
            priorities.set(identity, TARGET);
            if (priorities.read(identity) != TARGET) {
                priorities.set(identity, original);
                return OptimizationResult.failure("no se pudo verificar ABOVE_NORMAL");
            }
            return OptimizationResult.changed(identity.processName() + " PID " + processId + ": " + original + " -> " + TARGET);
        } catch (ProcessPriorityException exception) {
            return OptimizationResult.failure(exception.getMessage());
        }
    }
    public OptimizationResult restore(String originalState) {
        Snapshot snapshot;
        try { snapshot = decode(originalState); }
        catch (RuntimeException exception) { return OptimizationResult.failure("snapshot de prioridad inválido"); }
        if (!priorities.isSameProcess(snapshot.identity()))
            return OptimizationResult.unchanged("el proceso terminó o el PID fue reutilizado; restauración no requerida");
        try {
            ProcessPriority current = priorities.read(snapshot.identity());
            if (current == snapshot.original()) return OptimizationResult.unchanged("la prioridad ya estaba restaurada");
            priorities.set(snapshot.identity(), snapshot.original());
            if (priorities.read(snapshot.identity()) != snapshot.original())
                return OptimizationResult.failure("no se pudo verificar la prioridad restaurada");
            return OptimizationResult.changed(current + " -> " + snapshot.original());
        } catch (ProcessPriorityException exception) {
            if (exception.reason() == ProcessPriorityException.Reason.PROCESS_ENDED
                    || exception.reason() == ProcessPriorityException.Reason.PID_REUSED)
                return OptimizationResult.unchanged("el proceso ya no existe; restauración no requerida");
            return OptimizationResult.failure(exception.getMessage());
        }
    }

    private static String encode(ProcessIdentity value, ProcessPriority original) {
        String safeName = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.processName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return value.pid() + ":" + value.startEpochMillis() + ":" + safeName + ":" + original;
    }
    private static Snapshot decode(String value) {
        String[] parts = value.split(":", 4);
        String name = new String(java.util.Base64.getUrlDecoder().decode(parts[2]), java.nio.charset.StandardCharsets.UTF_8);
        return new Snapshot(new ProcessIdentity(Long.parseLong(parts[0]), Long.parseLong(parts[1]), name),
                ProcessPriority.valueOf(parts[3]));
    }
    private record Snapshot(ProcessIdentity identity, ProcessPriority original) { }
}
