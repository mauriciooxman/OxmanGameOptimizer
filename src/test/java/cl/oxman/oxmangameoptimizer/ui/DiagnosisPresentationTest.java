package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety;
import cl.oxman.oxmangameoptimizer.optimizer.assessment.*;
import cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessObservation;
import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosisPresentationTest {
    private static SystemOptimizationAssessment assessment(int actions) {
        var candidate = new BackgroundProcessObservation(new ProcessIdentity(101, 100, "OneDrive.exe"),
                "test", true, false, 4);
        var power = new OptimizationAssessmentItem("power-plan", "Plan de energía",
                actions > 0 ? AssessmentStatus.ACTION_AVAILABLE : AssessmentStatus.OPTIMIZED,
                OptimizationSafety.SAFE, "");
        var background = new OptimizationAssessmentItem("background-ecoqos:101", "Carga de fondo",
                AssessmentStatus.ACTION_AVAILABLE, OptimizationSafety.SAFE, "");
        return new SystemOptimizationAssessment(actions == 2 ? List.of(power, background) : List.of(power),
                new PreGameBaseline(1, 15, 50, 4_000_000_000L, 150, List.of(), true),
                List.of(), actions == 2 ? List.of(candidate) : List.of());
    }

    @Test void zeroOptimizations() {
        assertTrue(DiagnosisPresentation.client(assessment(0)).endsWith("0 optimizaciones seguras disponibles"));
    }

    @Test void oneOptimization() {
        assertTrue(DiagnosisPresentation.client(assessment(1)).endsWith("1 optimización segura disponible"));
    }

    @Test void twoOptimizations() {
        assertTrue(DiagnosisPresentation.client(assessment(2)).endsWith("2 optimizaciones seguras disponibles"));
    }

    @Test void zeroChanges() {
        String summary = DiagnosisPresentation.summary(new OptimizationReport(0, 0, 0, true), assessment(0));
        assertTrue(summary.contains("0 cambios aplicados."));
        assertTrue(summary.startsWith("SISTEMA LISTO\n"));
        assertFalse(summary.contains("verificados"));
    }

    @Test void oneChange() {
        String summary = DiagnosisPresentation.summary(new OptimizationReport(1, 1, 0, false), assessment(1));
        assertTrue(summary.contains("1 cambio aplicado y verificado. Es reversible."));
        assertTrue(summary.startsWith("OPTIMIZACIÓN COMPLETADA\n"));
    }

    @Test void twoChanges() {
        assertTrue(DiagnosisPresentation.summary(new OptimizationReport(2, 2, 0, false), assessment(2))
                .contains("2 cambios aplicados y verificados. Todos son reversibles."));
    }

    @Test void failureUsesCorrectCountWithoutClaimingVerification() {
        String[] expected = {"0 cambios aplicados.", "1 cambio aplicado.", "2 cambios aplicados."};
        for (int count = 0; count <= 2; count++) {
            String summary = DiagnosisPresentation.summary(new OptimizationReport(3, count, 1, false), assessment(2));
            assertTrue(summary.startsWith("ATENCIÓN REQUERIDA\n" + expected[count]));
            assertFalse(summary.contains("verificado"));
            assertFalse(summary.contains("Sistema preparado"));
        }
    }

    @Test void zeroChangesPreservesExplanationForAnObsoletePlan() {
        assertTrue(DiagnosisPresentation.summary(new OptimizationReport(1, 0, 0, true), assessment(1))
                .contains("0 cambios aplicados: los ajustes previstos ya no eran necesarios o aplicables."));
    }

    @Test void advancedSampleCountUsesSingular() {
        assertTrue(DiagnosisPresentation.advanced(assessment(0)).contains("Baseline: 1 muestra · completo: true"));
    }
}
