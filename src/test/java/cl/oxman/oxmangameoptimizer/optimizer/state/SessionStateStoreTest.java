package cl.oxman.oxmangameoptimizer.optimizer.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionStateStoreTest {
    @TempDir Path temporary;

    @Test
    void roundTripsRecoveryDataAndCompletes() throws Exception {
        SessionStateStore store = new SessionStateStore(temporary.resolve("session-state.json"));
        SessionState state = SessionState.begin("Juego con acentos á");
        state.add(new AppliedChange("power-plan", "guid-original"));
        state.add(new AppliedChange("service:DiagTrack", "4"));

        store.save(state);
        SessionState restored = store.load().orElseThrow();

        assertEquals(state.gameName(), restored.gameName());
        assertEquals(state.startedAt(), restored.startedAt());
        assertEquals(state.changes(), restored.changes());
        store.complete();
        assertTrue(store.load().isEmpty());
    }
}
