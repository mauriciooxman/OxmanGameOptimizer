package cl.oxman.oxmangameoptimizer.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogManagerTest {
    @Test void clientModeHidesTechnicalCommands() {
        LogManager.resetForTests();
        LogManager.addLog("PresentMon command: tool --output CSV");
        assertTrue(LogManager.entriesFor(ApplicationMode.CLIENT).isEmpty());
        assertTrue(LogManager.entriesFor(ApplicationMode.ADVANCED).getFirst().contains("PresentMon command:"));
    }

    @Test void explicitClientEventsRemainVisible() {
        LogManager.resetForTests();
        LogManager.addClientLog("Sistema preparado para jugar");
        assertTrue(LogManager.entriesFor(ApplicationMode.CLIENT).getFirst().contains("Sistema preparado para jugar"));
    }

    @Test void technicalErrorsRemainVisibleToClients() {
        LogManager.resetForTests();
        LogManager.addLog("❌ Error al restaurar");
        assertTrue(LogManager.entriesFor(ApplicationMode.CLIENT).getFirst().contains("Error al restaurar"));
    }
}
