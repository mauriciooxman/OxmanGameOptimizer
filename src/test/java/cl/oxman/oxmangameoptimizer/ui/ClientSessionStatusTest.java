package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome;
import cl.oxman.oxmangameoptimizer.optimizer.action.ProcessPriorityAction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientSessionStatusTest {
    @Test void failureDoesNotReportReady() {
        assertEquals("ATENCIÓN REQUERIDA", ClientSessionStatus.afterOptimization(new OptimizationReport(1,0,1,false)));
    }
    @Test void appliedChangesProduceActiveOptimizationStatus() {
        assertEquals("OPTIMIZACIÓN ACTIVA",
                ClientSessionStatus.afterOptimization(new OptimizationReport(2, 1, 0, false)));
    }

    @Test void noChangeProducesSystemReadyStatus() {
        assertEquals("SISTEMA LISTO",
                ClientSessionStatus.afterOptimization(new OptimizationReport(2, 0, 0, false)));
    }

    @Test void startingAndRunningStatusesNameTheGame() {
        assertEquals("SISTEMA LISTO · Iniciando Counter-Strike 2...",
                ClientSessionStatus.starting("Counter-Strike 2"));
        assertEquals("LISTO PARA JUGAR · Counter-Strike 2",
                ClientSessionStatus.running("Counter-Strike 2"));
    }

    @Test void runningStatusReportsHighApplied() {
        var report = new OptimizationReport(1, 1, 0, false, java.util.List.of(new ActionOutcome(
                ProcessPriorityAction.HIGH_ID_PREFIX + "42", ActionOutcome.Status.APPLIED, "verified")));
        assertEquals("LISTO PARA JUGAR · Counter-Strike 2 · Prioridad HIGH: aplicada",
                ClientSessionStatus.running("Counter-Strike 2", report));
    }

    @Test void runningStatusReportsHighUnavailableWithoutBlockingSession() {
        var report = new OptimizationReport(0, 0, 0, false, java.util.List.of(new ActionOutcome(
                ProcessPriorityAction.HIGH_ID_PREFIX + "pending", ActionOutcome.Status.NOT_APPLICABLE, "not running")));
        assertEquals("LISTO PARA JUGAR · Counter-Strike 2 · Prioridad HIGH: no disponible",
                ClientSessionStatus.running("Counter-Strike 2", report));
    }
}
