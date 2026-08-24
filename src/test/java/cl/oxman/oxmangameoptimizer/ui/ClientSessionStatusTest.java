package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientSessionStatusTest {
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
}
