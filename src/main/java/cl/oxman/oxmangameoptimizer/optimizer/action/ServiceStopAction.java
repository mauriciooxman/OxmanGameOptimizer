package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.CommandResult;
import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public final class ServiceStopAction implements OptimizationAction {
    private static final Pattern STATE = Pattern.compile("STATE\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final int STOPPED = 1;
    private static final int RUNNING = 4;
    private final String service;
    private final String description;
    private final SystemCommandRunner commands;
    private String original;

    public ServiceStopAction(String service, String description, SystemCommandRunner commands) {
        this.service = service;
        this.description = description;
        this.commands = commands;
    }

    @Override public String id() { return "service:" + service; }
    @Override public String name() { return service; }
    @Override public String description() { return description; }
    @Override public boolean isSupported() { return state() >= 0; }
    @Override public boolean requiresAdministrator() { return true; }
    @Override public boolean isReversible() { return true; }
    @Override public String originalState() { return original; }

    @Override
    public OptimizationResult apply() {
        int state = state();
        if (state < 0) return OptimizationResult.failure("servicio no disponible");
        original = Integer.toString(state);
        if (state == STOPPED) return OptimizationResult.unchanged("ya estaba detenido");
        CommandResult stop = commands.run(List.of("sc", "stop", service), Duration.ofSeconds(15));
        if (!stop.succeeded() || !waitFor(STOPPED)) {
            commands.run(List.of("sc", "start", service), Duration.ofSeconds(15));
            return OptimizationResult.failure("no se pudo confirmar la detención; se solicitó restauración inmediata");
        }
        return OptimizationResult.changed("Running -> Stopped");
    }

    @Override
    public OptimizationResult restore(String originalState) {
        if (!Integer.toString(RUNNING).equals(originalState)) return OptimizationResult.unchanged("no fue detenido por Oxman");
        if (state() == RUNNING) return OptimizationResult.unchanged("ya estaba iniciado");
        CommandResult start = commands.run(List.of("sc", "start", service), Duration.ofSeconds(15));
        return start.succeeded() && waitFor(RUNNING)
                ? OptimizationResult.changed("Stopped -> Running")
                : OptimizationResult.failure("no se pudo confirmar el inicio");
    }

    private boolean waitFor(int expected) {
        for (int i = 0; i < 5; i++) {
            if (state() == expected) return true;
            try { Thread.sleep(200); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    private int state() {
        CommandResult result = commands.run(List.of("sc", "query", service), Duration.ofSeconds(5));
        if (!result.succeeded()) return -1;
        var matcher = STATE.matcher(result.output());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
