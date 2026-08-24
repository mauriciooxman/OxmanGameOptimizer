package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessPriorityActionTest {
    @Test void readsNormalSetsAboveNormalVerifiesAndRestoresNormal() {
        Fake fake = new Fake(ProcessPriority.NORMAL); var action = new ProcessPriorityAction(42, fake);
        assertEquals(OptimizationSafety.EXPERIMENTAL, action.safety()); assertTrue(action.apply().success());
        assertEquals(ProcessPriority.ABOVE_NORMAL, fake.value); assertTrue(action.restore(action.originalState()).success());
        assertEquals(ProcessPriority.NORMAL, fake.value);
    }
    @Test void originalAboveNormalIsNotChanged() {
        Fake fake = new Fake(ProcessPriority.ABOVE_NORMAL); var action = new ProcessPriorityAction(42, fake);
        assertFalse(action.apply().changed()); assertEquals(0, fake.sets);
    }
    @Test void disappearedOrRecycledProcessIsNeverModifiedDuringRestore() {
        Fake fake = new Fake(ProcessPriority.NORMAL); var action = new ProcessPriorityAction(42, fake);
        action.apply(); fake.same = false; int calls = fake.sets;
        assertTrue(action.restore(action.originalState()).success()); assertEquals(calls, fake.sets);
    }
    @Test void accessDeniedAndInvalidPidAreUnsupported() {
        Fake denied = new Fake(ProcessPriority.NORMAL); denied.failure = ProcessPriorityException.Reason.ACCESS_DENIED;
        Fake invalid = new Fake(ProcessPriority.NORMAL); invalid.failure = ProcessPriorityException.Reason.INVALID_PID;
        assertFalse(new ProcessPriorityAction(42, denied).isSupported()); assertFalse(new ProcessPriorityAction(42, invalid).isSupported());
    }
    @Test void failedVerificationRestoresOriginal() {
        Fake fake = new Fake(ProcessPriority.NORMAL); fake.ignoreAbove = true;
        assertFalse(new ProcessPriorityAction(42, fake).apply().success()); assertEquals(ProcessPriority.NORMAL, fake.value);
    }
    private static final class Fake implements ProcessPriorityController {
        ProcessPriority value; boolean same = true, ignoreAbove; int sets; ProcessPriorityException.Reason failure;
        Fake(ProcessPriority value) { this.value = value; }
        public ProcessIdentity identify(long id) { if (failure != null) throw new ProcessPriorityException(failure, "test"); return new ProcessIdentity(id, 1234, "game.exe"); }
        public ProcessPriority read(ProcessIdentity process) { return value; }
        public void set(ProcessIdentity process, ProcessPriority priority) { sets++; if (!(ignoreAbove && priority == ProcessPriority.ABOVE_NORMAL)) value = priority; }
        public boolean isSameProcess(ProcessIdentity process) { return same; }
    }
}
