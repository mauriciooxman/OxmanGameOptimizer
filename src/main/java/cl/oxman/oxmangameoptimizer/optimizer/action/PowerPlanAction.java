package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.CommandResult;
import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public final class PowerPlanAction implements OptimizationAction {
    public static final String ID = "power-plan";
    private static final Pattern GUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final String HIGH_PERFORMANCE = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";
    private final SystemCommandRunner commands;
    private String original;

    public PowerPlanAction(SystemCommandRunner commands) { this.commands = commands; }
    @Override public String id() { return ID; }
    @Override public String name() { return "Power Plan"; }
    @Override public String description() { return "Activa High Performance durante la sesión"; }
    @Override public boolean isSupported() { return System.getProperty("os.name", "").startsWith("Windows"); }
    @Override public boolean requiresAdministrator() { return false; }
    @Override public boolean isReversible() { return true; }
    @Override public String originalState() { return original; }

    @Override
    public OptimizationResult apply() {
        original = activeGuid();
        if (original == null) return OptimizationResult.failure("no se pudo leer el plan activo");
        if (HIGH_PERFORMANCE.equalsIgnoreCase(original)) return OptimizationResult.unchanged("High Performance ya estaba activo");
        if (!setActive(HIGH_PERFORMANCE) || !HIGH_PERFORMANCE.equalsIgnoreCase(activeGuid())) {
            setActive(original);
            return OptimizationResult.failure("Windows no confirmó el cambio de plan");
        }
        return OptimizationResult.changed(original + " -> High Performance");
    }

    @Override
    public OptimizationResult restore(String originalState) {
        if (originalState == null || !GUID.matcher(originalState).matches())
            return OptimizationResult.failure("GUID original inválido");
        if (originalState.equalsIgnoreCase(activeGuid())) return OptimizationResult.unchanged("plan original ya activo");
        return setActive(originalState) && originalState.equalsIgnoreCase(activeGuid())
                ? OptimizationResult.changed("plan original restaurado: " + originalState)
                : OptimizationResult.failure("no se pudo confirmar el plan original");
    }

    private String activeGuid() {
        CommandResult result = commands.run(List.of("powercfg", "/getactivescheme"), Duration.ofSeconds(5));
        if (!result.succeeded()) return null;
        var matcher = GUID.matcher(result.output());
        return matcher.find() ? matcher.group() : null;
    }

    private boolean setActive(String guid) {
        return commands.run(List.of("powercfg", "/setactive", guid), Duration.ofSeconds(5)).succeeded();
    }
}
