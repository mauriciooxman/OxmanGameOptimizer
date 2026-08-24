package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/** Reversible EcoQoS action for one preselected background process. */
public final class BackgroundEcoQosAction implements OptimizationAction {
    public static final String ID_PREFIX = "background-ecoqos:";
    private final ProcessIdentity identity;
    private final ProcessPowerThrottlingService service;
    private final Consumer<String> log;
    private String originalState;
    private ProcessPowerThrottlingState appliedState;

    public BackgroundEcoQosAction(ProcessIdentity identity, ProcessPowerThrottlingService service, Consumer<String> log) {
        this.identity = identity; this.service = service; this.log = log;
    }
    public String id() { return ID_PREFIX + identity.pid(); }
    public String name() { return "Background EcoQoS (" + identity.processName() + " PID " + identity.pid() + ")"; }
    public String description() { return "Habilita temporalmente EXECUTION_SPEED throttling"; }
    public boolean isSupported() { return service.isSameProcess(identity); }
    public boolean requiresAdministrator() { return false; }
    public boolean isReversible() { return true; }
    public OptimizationSafety safety() { return OptimizationSafety.EXPERIMENTAL; }
    public String originalState() { return originalState; }

    public OptimizationResult apply() {
        try {
            ProcessPowerThrottlingState before = service.read(identity);
            originalState = encode(identity, before);
            if (before.isExecutionSpeedEnabled()) return OptimizationResult.unchanged("ya estaba en EcoQoS");
            appliedState = before.withExecutionSpeedEnabled();
            service.applyEcoQos(identity, before);
            if (!service.read(identity).equals(appliedState)) {
                service.restore(identity, before);
                return OptimizationResult.failure("no se pudo verificar EcoQoS");
            }
            return OptimizationResult.changed("EcoQoS aplicado");
        } catch (ProcessPowerThrottlingException exception) { return OptimizationResult.failure(exception.getMessage()); }
    }

    public OptimizationResult restore(String value) {
        Snapshot snapshot;
        try { snapshot = decode(value); }
        catch (RuntimeException exception) { return OptimizationResult.failure("snapshot EcoQoS inválido"); }
        if (!service.isSameProcess(snapshot.identity()))
            return OptimizationResult.unchanged("proceso terminado o PID reutilizado; restore no requerido");
        try {
            ProcessPowerThrottlingState current = service.read(snapshot.identity());
            ProcessPowerThrottlingState expected = snapshot.state().withExecutionSpeedEnabled();
            if (current.equals(snapshot.state())) return OptimizationResult.unchanged("estado ya restaurado");
            if (!current.equals(expected)) {
                log.accept("Background EcoQoS restore skipped: cambio externo preservado para " + snapshot.identity().processName());
                return OptimizationResult.unchanged("cambio externo preservado; restore omitido");
            }
            service.restore(snapshot.identity(), snapshot.state());
            if (!service.read(snapshot.identity()).equals(snapshot.state()))
                return OptimizationResult.failure("no se pudo verificar restore EcoQoS");
            return OptimizationResult.changed("estado nativo anterior restaurado");
        } catch (ProcessPowerThrottlingException exception) {
            if (exception.reason() == ProcessPowerThrottlingException.Reason.PROCESS_ENDED
                    || exception.reason() == ProcessPowerThrottlingException.Reason.PID_REUSED)
                return OptimizationResult.unchanged("proceso terminado; restore no requerido");
            return OptimizationResult.failure(exception.getMessage());
        }
    }

    private static String encode(ProcessIdentity identity, ProcessPowerThrottlingState state) {
        String name = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.processName().getBytes(StandardCharsets.UTF_8));
        return identity.pid() + ":" + identity.startEpochMillis() + ":" + name + ":" + state.version() + ":"
                + Integer.toUnsignedString(state.controlMask()) + ":" + Integer.toUnsignedString(state.stateMask());
    }
    private static Snapshot decode(String value) {
        String[] parts = value.split(":", 6);
        String name = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        return new Snapshot(new ProcessIdentity(Long.parseLong(parts[0]), Long.parseLong(parts[1]), name),
                new ProcessPowerThrottlingState(Integer.parseInt(parts[3]), Integer.parseUnsignedInt(parts[4]), Integer.parseUnsignedInt(parts[5])));
    }
    private record Snapshot(ProcessIdentity identity, ProcessPowerThrottlingState state) { }
}
