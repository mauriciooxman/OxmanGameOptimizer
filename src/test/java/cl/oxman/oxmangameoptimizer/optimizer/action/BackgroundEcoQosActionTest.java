package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundEcoQosActionTest {
    private static final ProcessIdentity ID = new ProcessIdentity(7, 700, "chrome.exe");

    @Test void appliesEcoQosAndRestoresExactNativeState() {
        StateService service = new StateService(new ProcessPowerThrottlingState(1, 0x4, 0x4));
        BackgroundEcoQosAction action = new BackgroundEcoQosAction(ID, service, ignored -> { });
        assertTrue(action.apply().changed()); assertTrue(service.state.isExecutionSpeedEnabled());
        assertTrue(action.restore(action.originalState()).changed());
        assertEquals(new ProcessPowerThrottlingState(1, 0x4, 0x4), service.state);
    }

    @Test void processTerminationAndPidReuseNeverWriteRestore() {
        StateService service = new StateService(new ProcessPowerThrottlingState(1, 0, 0));
        BackgroundEcoQosAction action = new BackgroundEcoQosAction(ID, service, ignored -> { });
        action.apply(); service.same = false;
        assertFalse(action.restore(action.originalState()).changed()); assertEquals(0, service.restoreCalls);
    }

    @Test void externalDriftIsPreservedByCompareAndRestore() {
        StateService service = new StateService(new ProcessPowerThrottlingState(1, 0, 0));
        BackgroundEcoQosAction action = new BackgroundEcoQosAction(ID, service, ignored -> { });
        action.apply(); service.state = new ProcessPowerThrottlingState(1, 0x8, 0x8);
        assertFalse(action.restore(action.originalState()).changed()); assertEquals(0, service.restoreCalls);
    }

    static final class StateService implements ProcessPowerThrottlingService {
        ProcessPowerThrottlingState state; boolean same = true; int restoreCalls;
        StateService(ProcessPowerThrottlingState state) { this.state = state; }
        public ProcessIdentity identify(long pid) { return ID; }
        public ProcessPowerThrottlingState read(ProcessIdentity process) { return state; }
        public void applyHighQos(ProcessIdentity process) { throw new AssertionError("game must not be modified"); }
        public void applyEcoQos(ProcessIdentity process, ProcessPowerThrottlingState current) { state = current.withExecutionSpeedEnabled(); }
        public void restore(ProcessIdentity process, ProcessPowerThrottlingState value) { restoreCalls++; state = value; }
        public boolean isSameProcess(ProcessIdentity process) { return same; }
    }
}
