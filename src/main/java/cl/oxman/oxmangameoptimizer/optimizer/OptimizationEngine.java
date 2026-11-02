package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome;
import static cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome.Status.*;
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
import java.util.Set;
import java.util.Objects;
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
        this.actions = new ArrayList<>(actions);
        this.store = store;
        this.log = log;
        this.includeExperimental = includeExperimental;
    }

    public synchronized OptimizationReport apply(String gameName) {
        return apply(gameName, actions.stream().map(OptimizationAction::id).collect(Collectors.toSet()));
    }

    public synchronized OptimizationReport apply(String gameName, Set<String> plannedActionIds) {
        if (hasIncompleteSession()) return new OptimizationReport(0, 0, 1, false,
                List.of(new ActionOutcome("session", FAILED, "Restauración pendiente; snapshot conservado")));
        if (plannedActionIds.isEmpty()) return new OptimizationReport(0, 0, 0, true);
        current = SessionState.begin(gameName);
        if (!persist()) {
            current = null;
            return new OptimizationReport(0, 0, 1, false,
                    List.of(new ActionOutcome("session", FAILED, "Snapshot no disponible; BOOST cancelado")));
        }
        return execute(actions, plannedActionIds);
    }

    /**
     * Adds one reversible action to the active journal. This supports state that only
     * becomes available after BOOST starts, such as the real game process identity.
     */
    public synchronized OptimizationReport applyAdditional(String gameName, OptimizationAction action) {
        Objects.requireNonNull(gameName);
        Objects.requireNonNull(action);
        boolean newSession = false;
        if (current == null) {
            if (hasIncompleteSession()) return new OptimizationReport(0, 0, 1, false,
                    List.of(new ActionOutcome("session", FAILED, "Restauración pendiente; snapshot conservado")));
            current = SessionState.begin(gameName);
            newSession = true;
            if (!persist()) {
                current = null;
                return new OptimizationReport(0, 0, 1, false,
                        List.of(new ActionOutcome("session", FAILED, "Snapshot no disponible; acción cancelada")));
            }
        }
        if (current.changes().stream().anyMatch(change -> change.actionId().equals(action.id()))) {
            return new OptimizationReport(1, 0, 0, false,
                    List.of(new ActionOutcome(action.id(), ALREADY_OPTIMIZED,
                            "Acción ya registrada para esta sesión")));
        }
        actions.stream().filter(existing -> existing.id().equals(action.id())).findFirst()
                .ifPresentOrElse(ignored -> { }, () -> actions.add(action));
        OptimizationReport report = execute(List.of(action), Set.of(action.id()));
        if (newSession && report.applicable() == 0 && report.failed() == 0
                && current.changes().isEmpty()) {
            try { store.complete(); current = null; }
            catch (IOException exception) { log.accept("⚠ No se pudo cerrar el snapshot vacío: " + exception.getMessage()); }
        }
        return report;
    }

    private OptimizationReport execute(List<OptimizationAction> candidates, Set<String> plannedActionIds) {
        int applicable = 0, applied = 0, failed = 0;
        List<ActionOutcome> outcomes = new ArrayList<>();
        for (OptimizationAction action : candidates) {
            if (!plannedActionIds.contains(action.id())) continue;
            if (!action.isReversible() || (action.safety() == OptimizationSafety.EXPERIMENTAL && !includeExperimental)) {
                outcomes.add(new ActionOutcome(action.id(), SKIPPED, "Acción no permitida"));
                continue;
            }
            boolean journaled = false;
            try {
                if (!action.isSupported()) {
                    outcomes.add(new ActionOutcome(action.id(), NOT_APPLICABLE, action.unsupportedDetail()));
                    continue;
                }
                applicable++;
                String prepared = action.prepareOriginalState();
                if (prepared != null) {
                    current.add(new AppliedChange(action.id(), prepared));
                    if (!persist()) {
                        outcomes.add(new ActionOutcome(action.id(), FAILED, "Snapshot no disponible; acción no ejecutada"));
                        failed++;
                        break;
                    }
                    journaled = true;
                }
                OptimizationResult result = action.apply();
                if (!result.success()) {
                    failed++;
                    outcomes.add(new ActionOutcome(action.id(), FAILED, result.error()));
                    // Keep a prepared snapshot: even partial native failure must remain recoverable.
                } else if (result.changed()) {
                    if (!journaled) {
                        current.add(new AppliedChange(action.id(), action.originalState()));
                        if (!persist()) {
                            failed++;
                            action.restore(action.originalState());
                            outcomes.add(new ActionOutcome(action.id(), FAILED, "No se pudo guardar recuperación"));
                            break;
                        }
                    }
                    applied++;
                    outcomes.add(new ActionOutcome(action.id(), APPLIED, result.detail()));
                } else {
                    outcomes.add(new ActionOutcome(action.id(), result.detail().contains("CONFIGURATION_DRIFT")
                            ? SKIPPED : ALREADY_OPTIMIZED, result.detail()));
                }
            } catch (RuntimeException exception) {
                failed++;
                outcomes.add(new ActionOutcome(action.id(), FAILED, String.valueOf(exception.getMessage())));
            }
        }
        Set<String> reported = outcomes.stream().map(ActionOutcome::actionId).collect(Collectors.toSet());
        plannedActionIds.stream().filter(id -> !reported.contains(id)).forEach(id ->
                outcomes.add(new ActionOutcome(id, SKIPPED, "Acción no ejecutada; condiciones o recuperación no disponibles")));
        outcomes.forEach(o -> log.accept(o.actionId() + " · " + o.status() + " · " + o.detail()));
        return new OptimizationReport(applicable, applied, failed, false, outcomes);
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
        try { return store.load().isPresent(); } catch (IOException | RuntimeException exception) { return true; }
    }

    private boolean persist() {
        try { store.save(current); return true; }
        catch (IOException exception) { log.accept("❌ No se pudo guardar recuperación: " + exception.getMessage()); return false; }
    }
}
