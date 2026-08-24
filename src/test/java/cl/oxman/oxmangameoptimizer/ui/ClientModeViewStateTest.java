package cl.oxman.oxmangameoptimizer.ui;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ClientModeViewStateTest {
    @Test void newInstallationStartsInClientMode() {
        ClientModeViewState state = ClientModeViewState.initial();
        assertEquals(ApplicationMode.CLIENT, state.mode());
        assertFalse(state.performanceLabVisible());
        assertFalse(state.experimentsVisible());
    }

    @Test void advancedModeExposesExistingLaboratory() {
        ClientModeViewState state = ClientModeViewState.forMode(ApplicationMode.ADVANCED);
        assertTrue(state.performanceLabVisible());
        assertTrue(state.experimentsVisible());
        assertTrue(state.technicalLogVisible());
    }

    @Test void fxmlKeepsPerformanceLabCollapsedByDefault() throws IOException {
        try (var stream = getClass().getResourceAsStream("/cl/oxman/oxmangameoptimizer/main-view.fxml")) {
            assertNotNull(stream);
            String fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(fxml.contains("fx:id=\"performanceLabPanel\""));
            assertTrue(fxml.contains("visible=\"false\""));
            assertTrue(fxml.contains("managed=\"false\""));
            assertTrue(fxml.contains("fx:id=\"advancedModeToggle\""));
        }
    }
}
