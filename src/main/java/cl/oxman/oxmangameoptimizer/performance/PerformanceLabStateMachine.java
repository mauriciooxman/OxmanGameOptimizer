package cl.oxman.oxmangameoptimizer.performance;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class PerformanceLabStateMachine {
    private static final Map<PerformanceLabState, EnumSet<PerformanceLabState>> ALLOWED = new EnumMap<>(PerformanceLabState.class);
    static {
        allow(PerformanceLabState.IDLE, PerformanceLabState.MEASURING_BASELINE, PerformanceLabState.WAITING_FOR_GAME);
        allow(PerformanceLabState.MEASURING_BASELINE, PerformanceLabState.WAITING_FOR_GAME, PerformanceLabState.FAILED);
        allow(PerformanceLabState.WAITING_FOR_GAME, PerformanceLabState.CAPTURING, PerformanceLabState.FAILED);
        allow(PerformanceLabState.CAPTURING, PerformanceLabState.APPLYING_BOOST, PerformanceLabState.FAILED);
        allow(PerformanceLabState.APPLYING_BOOST, PerformanceLabState.STABILIZING, PerformanceLabState.FAILED);
        allow(PerformanceLabState.STABILIZING, PerformanceLabState.CAPTURING_OPTIMIZED, PerformanceLabState.FAILED);
        allow(PerformanceLabState.CAPTURING_OPTIMIZED, PerformanceLabState.ANALYZING, PerformanceLabState.FAILED);
        allow(PerformanceLabState.ANALYZING, PerformanceLabState.COMPLETED, PerformanceLabState.FAILED);
        allow(PerformanceLabState.COMPLETED, PerformanceLabState.IDLE);
        allow(PerformanceLabState.FAILED, PerformanceLabState.IDLE);
    }
    private PerformanceLabState state = PerformanceLabState.IDLE;
    public synchronized PerformanceLabState state() { return state; }
    public synchronized void transitionTo(PerformanceLabState next) {
        if (!ALLOWED.getOrDefault(state, EnumSet.noneOf(PerformanceLabState.class)).contains(next))
            throw new IllegalStateException("Invalid Performance Lab transition: " + state + " -> " + next);
        state = next;
    }
    private static void allow(PerformanceLabState from, PerformanceLabState... to) { ALLOWED.put(from, EnumSet.of(to[0], to)); }
}
