package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.ProcessPriorityController;
import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import cl.oxman.oxmangameoptimizer.system.ProcessPriority;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityException;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityReading;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;

/** Reversible priority change guarded by PID, creation time and post-write verification. */
public final class ProcessPriorityAction implements OptimizationAction {
    public static final String ID_PREFIX = "process-priority:";
    public static final String HIGH_ID_PREFIX = "process-priority-high:";
    private final long processId;
    private final ProcessIdentity expectedIdentity;
    private final ProcessPriorityController priorities;
    private final Consumer<String> log;
    private final ProcessPriority target;
    private final OptimizationSafety safety;
    private final String idPrefix;
    private String originalState;
    private ProcessIdentity identity;
    private ProcessPriority original;
    private boolean prepared;
    private String unsupportedDetail = "Prioridad de proceso no disponible";

    /** Keeps the existing Above Normal experiment used by Performance Lab. */
    public ProcessPriorityAction(long processId, ProcessPriorityController priorities) {
        this(processId, priorities, ignored -> { });
    }

    /** Keeps the existing Above Normal experiment used by Performance Lab. */
    public ProcessPriorityAction(long processId, ProcessPriorityController priorities, Consumer<String> log) {
        this(processId, null, priorities, log, ProcessPriority.ABOVE_NORMAL,
                OptimizationSafety.EXPERIMENTAL, ID_PREFIX);
    }

    /** Creates the explicit-user BOOST action. It never targets Realtime. */
    public static ProcessPriorityAction high(ProcessIdentity identity, ProcessPriorityController priorities,
                                             Consumer<String> log) {
        Objects.requireNonNull(identity);
        return new ProcessPriorityAction(identity.pid(), identity, priorities, log, ProcessPriority.HIGH,
                OptimizationSafety.SAFE, HIGH_ID_PREFIX);
    }

    /** Builds the matching restorer for a persisted HIGH snapshot. */
    public static ProcessPriorityAction highRecovery(long processId, ProcessPriorityController priorities,
                                                     Consumer<String> log) {
        return new ProcessPriorityAction(processId, null, priorities, log, ProcessPriority.HIGH,
                OptimizationSafety.SAFE, HIGH_ID_PREFIX);
    }

    private ProcessPriorityAction(long processId, ProcessIdentity expectedIdentity,
                                  ProcessPriorityController priorities, Consumer<String> log,
                                  ProcessPriority target, OptimizationSafety safety, String idPrefix) {
        if (target == ProcessPriority.REALTIME) throw new IllegalArgumentException("Realtime no está permitido");
        this.processId = processId;
        this.expectedIdentity = expectedIdentity;
        this.priorities = Objects.requireNonNull(priorities);
        this.log = Objects.requireNonNull(log);
        this.target = Objects.requireNonNull(target);
        this.safety = Objects.requireNonNull(safety);
        this.idPrefix = Objects.requireNonNull(idPrefix);
    }

    public String id() { return idPrefix + processId; }
    public String name() { return "Game Process Priority"; }
    public String description() { return "Prioridad " + target + " temporal para el proceso real del juego"; }
    public boolean isSupported() {
        try {
            identity = resolveIdentity();
            priorities.read(identity);
            unsupportedDetail = "Prioridad de proceso disponible";
            return true;
        } catch (ProcessPriorityException exception) {
            unsupportedDetail = switch (exception.reason()) {
                case ACCESS_DENIED -> "Windows rechazó el acceso al proceso; prioridad no aplicada";
                case PROCESS_ENDED -> "El juego terminó antes de aplicar la prioridad";
                case PID_REUSED -> "El PID ya no pertenece al juego detectado";
                case INVALID_PID -> "PID de juego inválido";
                case NATIVE_FAILURE -> "Windows no pudo consultar la prioridad: " + exception.getMessage();
            };
            // For explicit HIGH, access denial is an attempted action failure, not a silent
            // unsupported state. prepare/apply will report it without performing a write.
            if (target == ProcessPriority.HIGH
                    && exception.reason() == ProcessPriorityException.Reason.ACCESS_DENIED) return true;
            return false;
        }
    }
    public String unsupportedDetail() { return unsupportedDetail; }
    public boolean requiresAdministrator() { return false; }
    public boolean isReversible() { return true; }
    public OptimizationSafety safety() { return safety; }
    public String originalState() { return originalState; }

    public String prepareOriginalState() {
        try {
            identity = resolveIdentity();
            ProcessPriorityReading initial = priorities.readWithNativeValue(identity);
            original = initial.priority();
            originalState = encode(identity, original, target);
            prepared = true;
            return originalState;
        } catch (ProcessPriorityException exception) {
            throw new ProcessPriorityException(exception.reason(), priorityError(exception));
        }
    }
    public OptimizationResult apply() {
        try {
            if (!prepared) prepareOriginalState();
            ProcessPriorityReading current = priorities.readWithNativeValue(identity);
            if (current.priority() != original)
                return OptimizationResult.unchanged("CONFIGURATION_DRIFT: prioridad cambiada antes de aplicar");
            if (original == target) return OptimizationResult.unchanged("ya estaba en " + target);
            if (original == ProcessPriority.REALTIME
                    || (target != ProcessPriority.HIGH && original == ProcessPriority.HIGH))
                return OptimizationResult.failure("prioridad inicial no modificable de forma segura: " + current.display());
            priorities.set(identity, target);
            log.accept("Priority apply: " + current.display() + " -> " + target.displayWithWindowsValue());
            ProcessPriorityReading verified = priorities.readWithNativeValue(identity);
            log.accept("Priority verified: " + verified.display());
            if (verified.priority() != target) {
                priorities.set(identity, original);
                return OptimizationResult.failure("no se pudo verificar " + target);
            }
            return OptimizationResult.changed(identity.processName() + " PID " + processId + ": "
                    + original + " -> " + target);
        } catch (ProcessPriorityException exception) {
            return OptimizationResult.failure(priorityError(exception));
        }
    }
    public OptimizationResult restore(String originalState) {
        Snapshot snapshot;
        try { snapshot = decode(originalState); }
        catch (RuntimeException exception) { return OptimizationResult.failure("snapshot de prioridad inválido"); }
        if (snapshot.applied() != target)
            return OptimizationResult.failure("snapshot de prioridad no corresponde a " + target);
        if (!priorities.isSameProcess(snapshot.identity()))
            return OptimizationResult.unchanged("el proceso terminó o el PID fue reutilizado; restauración no requerida");
        try {
            ProcessPriorityReading current = priorities.readWithNativeValue(snapshot.identity());
            if (current.priority() == snapshot.original()) return OptimizationResult.unchanged("la prioridad ya estaba restaurada");
            if (current.priority() != snapshot.applied()) {
                log.accept("Priority restore skipped: current priority no longer matches Oxman's applied value: "
                        + current.display() + " (expected " + snapshot.applied().displayWithWindowsValue() + ")");
                return OptimizationResult.unchanged("cambio externo preservado; restore omitido");
            }
            if (snapshot.original() == ProcessPriority.REALTIME)
                return OptimizationResult.failure("snapshot solicita restaurar Realtime; operación rechazada");
            priorities.set(snapshot.identity(), snapshot.original());
            log.accept("Priority restore: " + current.display() + " -> " + snapshot.original().displayWithWindowsValue());
            ProcessPriorityReading verified = priorities.readWithNativeValue(snapshot.identity());
            log.accept("Priority restore verified: " + verified.display());
            if (verified.priority() != snapshot.original())
                return OptimizationResult.failure("no se pudo verificar la prioridad restaurada");
            return OptimizationResult.changed(current.priority() + " -> " + snapshot.original());
        } catch (ProcessPriorityException exception) {
            if (exception.reason() == ProcessPriorityException.Reason.PROCESS_ENDED
                    || exception.reason() == ProcessPriorityException.Reason.PID_REUSED)
                return OptimizationResult.unchanged("el proceso ya no existe; restauración no requerida");
            return OptimizationResult.failure(priorityError(exception));
        }
    }

    private ProcessIdentity resolveIdentity() {
        if (expectedIdentity == null) return priorities.identify(processId);
        ProcessIdentity current = priorities.identify(processId);
        if (current.startEpochMillis() != expectedIdentity.startEpochMillis())
            throw new ProcessPriorityException(ProcessPriorityException.Reason.PID_REUSED,
                    "El PID ya no pertenece al juego detectado");
        return expectedIdentity;
    }

    private static String priorityError(ProcessPriorityException exception) {
        return exception.reason() == ProcessPriorityException.Reason.ACCESS_DENIED
                ? "Windows rechazó el cambio de prioridad: " + exception.getMessage()
                : exception.getMessage();
    }

    private static String encode(ProcessIdentity value, ProcessPriority original, ProcessPriority applied) {
        String safeName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.processName().getBytes(StandardCharsets.UTF_8));
        return value.pid() + ":" + value.startEpochMillis() + ":" + safeName + ":" + original + ":" + applied;
    }
    private static Snapshot decode(String value) {
        String[] parts = value.split(":", 5);
        if (parts.length < 4) throw new IllegalArgumentException("snapshot incompleto");
        String name = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        ProcessPriority applied = parts.length == 5 ? ProcessPriority.valueOf(parts[4]) : ProcessPriority.ABOVE_NORMAL;
        return new Snapshot(new ProcessIdentity(Long.parseLong(parts[0]), Long.parseLong(parts[1]), name),
                ProcessPriority.valueOf(parts[3]), applied);
    }
    private record Snapshot(ProcessIdentity identity, ProcessPriority original, ProcessPriority applied) { }
}
