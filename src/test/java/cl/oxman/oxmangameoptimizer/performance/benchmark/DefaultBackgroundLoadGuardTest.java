package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultBackgroundLoadGuardTest {
    private static final ProcessIdentity GAME = new ProcessIdentity(99, 9900, "game.exe");

    @Test void noSafeCandidatesReturnsNoChangeWithoutApplying() {
        StateService service = new StateService(new ProcessPowerThrottlingState(1, 0, 0));
        DefaultBackgroundLoadGuard guard = guard(List.of(observation(1, "discord.exe", 20)), service);
        assertEquals(ConfigurationValidity.NO_CHANGE, guard.apply(GAME, "Game").validity());
        assertEquals(0, service.restoreCalls);
    }

    @Test void allCandidatesAlreadyEcoQosReturnsNoChange() {
        StateService service = new StateService(new ProcessPowerThrottlingState(1, 1, 1));
        DefaultBackgroundLoadGuard guard = guard(List.of(observation(1, "chrome.exe", 20)), service);
        assertEquals(ConfigurationValidity.NO_CHANGE, guard.apply(GAME, "Game").validity());
    }

    private static DefaultBackgroundLoadGuard guard(List<BackgroundProcessObservation> values, StateService service) {
        return new DefaultBackgroundLoadGuard(duration -> values, new BackgroundProcessSelector(), service, ignored -> { });
    }
    private static BackgroundProcessObservation observation(long pid, String name, double cpu) {
        return new BackgroundProcessObservation(new ProcessIdentity(pid, pid * 100, name), "user", true, false, cpu);
    }
    private static final class StateService implements ProcessPowerThrottlingService {
        ProcessPowerThrottlingState state; int restoreCalls;
        StateService(ProcessPowerThrottlingState state) { this.state = state; }
        public ProcessIdentity identify(long pid) { return new ProcessIdentity(pid, pid * 100, "chrome.exe"); }
        public ProcessPowerThrottlingState read(ProcessIdentity process) { return state; }
        public void applyHighQos(ProcessIdentity process) { throw new AssertionError("game must not be modified"); }
        public void applyEcoQos(ProcessIdentity process, ProcessPowerThrottlingState current) { state = current.withExecutionSpeedEnabled(); }
        public void restore(ProcessIdentity process, ProcessPowerThrottlingState value) { restoreCalls++; state = value; }
        public boolean isSameProcess(ProcessIdentity process) { return true; }
    }
}
