package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessPowerThrottlingActionTest {
    private static final ProcessIdentity IDENTITY = new ProcessIdentity(42, 1234, "game.exe");

    @Test void appliesVerifiesAndRestoresExactNativeState() {
        FakeService service = new FakeService(new ProcessPowerThrottlingState(1, 0, 0));
        ProcessPowerThrottlingAction action = new ProcessPowerThrottlingAction(42, service, ignored -> { });
        assertTrue(action.apply().success());
        assertTrue(service.state.isExecutionSpeedDisabled());
        assertTrue(action.restore(action.originalState()).success());
        assertEquals(new ProcessPowerThrottlingState(1, 0, 0), service.state);
    }

    @Test void restoreRejectsReusedPid() {
        FakeService service = new FakeService(new ProcessPowerThrottlingState(1, 0, 0));
        ProcessPowerThrottlingAction action = new ProcessPowerThrottlingAction(42, service, ignored -> { });
        assertTrue(action.apply().success());
        service.sameProcess = false;
        assertFalse(action.restore(action.originalState()).changed());
        assertTrue(service.state.isExecutionSpeedDisabled());
    }

    @Test void restorePreservesExternalConfigurationDrift() {
        FakeService service = new FakeService(new ProcessPowerThrottlingState(1, 0, 0));
        ProcessPowerThrottlingAction action = new ProcessPowerThrottlingAction(42, service, ignored -> { });
        assertTrue(action.apply().success());
        service.state = new ProcessPowerThrottlingState(1, 0, 0);
        assertFalse(action.restore(action.originalState()).changed());
        assertEquals(0, service.state.controlMask());
    }

    private static final class FakeService implements ProcessPowerThrottlingService {
        ProcessPowerThrottlingState state; boolean sameProcess = true;
        FakeService(ProcessPowerThrottlingState state) { this.state = state; }
        public ProcessIdentity identify(long pid) { return IDENTITY; }
        public ProcessPowerThrottlingState read(ProcessIdentity process) { return state; }
        public void applyHighQos(ProcessIdentity process) { state = new ProcessPowerThrottlingState(CURRENT_VERSION, EXECUTION_SPEED, 0); }
        public void applyEcoQos(ProcessIdentity process, ProcessPowerThrottlingState current) { state = current.withExecutionSpeedEnabled(); }
        public void restore(ProcessIdentity process, ProcessPowerThrottlingState state) { this.state = state; }
        public boolean isSameProcess(ProcessIdentity process) { return sameProcess; }
    }
}
