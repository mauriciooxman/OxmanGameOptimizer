package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/** Experimental HighQoS action; it never changes the process priority class. */
public final class ProcessPowerThrottlingAction implements OptimizationAction {
    public static final String ID_PREFIX = "process-power-throttling:";
    private static final ProcessPowerThrottlingState HIGH_QOS = new ProcessPowerThrottlingState(
            ProcessPowerThrottlingService.CURRENT_VERSION, ProcessPowerThrottlingService.EXECUTION_SPEED, 0);
    private final long processId;
    private final ProcessPowerThrottlingService service;
    private final Consumer<String> log;
    private String originalState;
    private ProcessIdentity identity;
    private ApplyOutcome applyOutcome = ApplyOutcome.NOT_RUN;

    public ProcessPowerThrottlingAction(long processId, ProcessPowerThrottlingService service, Consumer<String> log) {
        this.processId = processId; this.service = service; this.log = log;
    }
    public String id() { return ID_PREFIX + processId; }
    public String name() { return "Process Power Throttling (HighQoS)"; }
    public String description() { return "Desactiva únicamente EXECUTION_SPEED throttling"; }
    public boolean isSupported() {
        try { identity = service.identify(processId); service.read(identity); return true; }
        catch (ProcessPowerThrottlingException exception) { return false; }
    }
    public boolean requiresAdministrator() { return false; }
    public boolean isReversible() { return true; }
    public OptimizationSafety safety() { return OptimizationSafety.EXPERIMENTAL; }
    public String originalState() { return originalState; }
    public boolean wasAlreadyHighQos() { return applyOutcome == ApplyOutcome.ALREADY_HIGH_QOS; }
    public boolean changedHighQos() { return applyOutcome == ApplyOutcome.CHANGED; }

    public OptimizationResult apply() {
        applyOutcome = ApplyOutcome.NOT_RUN;
        try {
            identity = service.identify(processId);
            ProcessPowerThrottlingState before = service.read(identity);
            originalState = encode(identity, before);
            log.accept("Power throttling before: " + before.nativeDisplay());
            if (before.isExecutionSpeedDisabled()) {
                applyOutcome = ApplyOutcome.ALREADY_HIGH_QOS;
                return OptimizationResult.unchanged("ya estaba en HighQoS");
            }
            service.applyHighQos(identity);
            log.accept("Power throttling apply: EXECUTION_SPEED -> DISABLED");
            ProcessPowerThrottlingState verified = service.read(identity);
            if (!verified.isExecutionSpeedDisabled()) {
                service.restore(identity, before);
                return OptimizationResult.failure("no se pudo verificar HighQoS");
            }
            log.accept("Power throttling verified: HighQoS / EXECUTION_SPEED disabled (" + verified.nativeDisplay() + ")");
            applyOutcome = ApplyOutcome.CHANGED;
            return OptimizationResult.changed(identity.processName() + " PID " + processId + ": HighQoS");
        } catch (ProcessPowerThrottlingException exception) { return OptimizationResult.failure(exception.getMessage()); }
    }

    public OptimizationResult restore(String value) {
        Snapshot snapshot;
        try { snapshot = decode(value); }
        catch (RuntimeException exception) { return OptimizationResult.failure("snapshot de power throttling inválido"); }
        if (!service.isSameProcess(snapshot.identity()))
            return OptimizationResult.unchanged("el proceso terminó o el PID fue reutilizado; restauración no requerida");
        try {
            ProcessPowerThrottlingState current = service.read(snapshot.identity());
            if (current.equals(snapshot.state())) return OptimizationResult.unchanged("power throttling ya estaba restaurado");
            if (!current.equals(HIGH_QOS)) {
                log.accept("Power throttling restore skipped: estado actual ya no coincide con HighQoS de Oxman ("
                        + current.nativeDisplay() + ")");
                return OptimizationResult.unchanged("cambio externo preservado; restore omitido");
            }
            service.restore(snapshot.identity(), snapshot.state());
            ProcessPowerThrottlingState verified = service.read(snapshot.identity());
            if (!verified.equals(snapshot.state())) return OptimizationResult.failure("no se pudo verificar la restauración");
            log.accept("Power throttling restore verified: " + verified.nativeDisplay());
            return OptimizationResult.changed("estado nativo anterior restaurado");
        } catch (ProcessPowerThrottlingException exception) {
            if (exception.reason() == ProcessPowerThrottlingException.Reason.PROCESS_ENDED
                    || exception.reason() == ProcessPowerThrottlingException.Reason.PID_REUSED)
                return OptimizationResult.unchanged("el proceso ya no existe; restauración no requerida");
            return OptimizationResult.failure(exception.getMessage());
        }
    }

    private static String encode(ProcessIdentity identity, ProcessPowerThrottlingState state) {
        String name = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.processName().getBytes(StandardCharsets.UTF_8));
        return identity.pid() + ":" + identity.startEpochMillis() + ":" + name + ":"
                + state.version() + ":" + Integer.toUnsignedString(state.controlMask()) + ":" + Integer.toUnsignedString(state.stateMask());
    }
    private static Snapshot decode(String value) {
        String[] parts = value.split(":", 6);
        String name = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        return new Snapshot(new ProcessIdentity(Long.parseLong(parts[0]), Long.parseLong(parts[1]), name),
                new ProcessPowerThrottlingState(Integer.parseInt(parts[3]), Integer.parseUnsignedInt(parts[4]), Integer.parseUnsignedInt(parts[5])));
    }
    private record Snapshot(ProcessIdentity identity, ProcessPowerThrottlingState state) { }
    private enum ApplyOutcome { NOT_RUN, ALREADY_HIGH_QOS, CHANGED }
}
