package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationResult;
import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety;
import cl.oxman.oxmangameoptimizer.optimizer.state.AppliedChange;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionState;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class OptimizationEngine {
    private final List<OptimizationAction> actions;
    private final SessionStateStore store;
    private final Consumer<String> log;
    private final boolean includeExperimental;
    private SessionState current;

    public OptimizationEngine(List<OptimizationAction> actions, SessionStateStore store, Consumer<String> log) {
        this(actions, store, log, false);
    }

    public OptimizationEngine(List<OptimizationAction> actions, SessionStateStore store, Consumer<String> log,
                              boolean includeExperimental) {
        this.actions = List.copyOf(actions);
        this.store = store;
        this.log = log;
        this.includeExperimental = includeExperimental;
    }

    public synchronized OptimizationReport apply(String gameName) {
        current = SessionState.begin(gameName);
        if (!persist()) return new OptimizationReport(0, 0, 1, false);
        int applicable = 0;
        int applied = 0;
        int failed = 0;
        for (OptimizationAction action : actions) {
            if (action.safety() == OptimizationSafety.EXPERIMENTAL && !includeExperimental) {
                log.accept("ℹ " + action.name() + ": experimental, no habilitada");
                continue;
            }
            if (!action.isSupported()) {
                log.accept("ℹ " + action.name() + ": no disponible");
                continue;
            }
            applicable++;
            OptimizationResult result;
            try { result = action.apply(); }
            catch (RuntimeException exception) { result = OptimizationResult.failure(exception.getMessage()); }
            if (!result.success()) {
                failed++;
                log.accept("⚠ " + action.name() + ": " + result.error()
                        + (action.requiresAdministrator() ? " (requiere administrador)" : ""));
                continue;
            }
            if (result.changed()) {
                current.add(new AppliedChange(action.id(), action.originalState()));
                if (!persist()) {
                    failed++;
                    action.restore(action.originalState());
                    log.accept("⚠ " + action.name() + ": cambio revertido porque no se pudo guardar el snapshot");
                    continue;
                }
                applied++;
            }
            log.accept("✔ " + action.name() + ": " + result.detail());
        }
        return new OptimizationReport(applicable, applied, failed, false);
    }

    public synchronized OptimizationReport restore() {
        SessionState state = current;
        if (state == null) {
            try { state = store.load().orElse(null); }
            catch (IOException exception) { log.accept("❌ Snapshot ilegible: " + exception.getMessage()); return new OptimizationReport(0, 0, false); }
        }
        if (state == null) return new OptimizationReport(0, 0, true);
        Map<String, OptimizationAction> byId = actions.stream().collect(Collectors.toMap(OptimizationAction::id, action -> action));
        List<AppliedChange> reverse = new ArrayList<>(state.changes());
        Collections.reverse(reverse);
        boolean restored = true;
        int failed = 0;
        for (AppliedChange change : reverse) {
            OptimizationAction action = byId.get(change.actionId());
            if (action == null) { log.accept("❌ No existe restaurador para " + change.actionId()); restored = false; failed++; continue; }
            OptimizationResult result;
            try { result = action.restore(change.originalState()); }
            catch (RuntimeException exception) { result = OptimizationResult.failure(exception.getMessage()); }
            if (result.success()) log.accept("✔ " + action.name() + " restaurado: " + result.detail());
            else { log.accept("❌ " + action.name() + ": " + result.error()); restored = false; failed++; }
        }
        if (restored) {
            try { store.complete(); current = null; }
            catch (IOException exception) { log.accept("⚠ No se pudo cerrar el snapshot: " + exception.getMessage()); restored = false; }
        }
        return new OptimizationReport(actions.size(), reverse.size(), failed, restored);
    }

    public synchronized boolean hasIncompleteSession() {
        try { return store.load().isPresent(); } catch (IOException exception) { return true; }
    }

    private boolean persist() {
        try { store.save(current); return true; }
        catch (IOException exception) { log.accept("❌ No se pudo guardar recuperación: " + exception.getMessage()); return false; }
    }
}
