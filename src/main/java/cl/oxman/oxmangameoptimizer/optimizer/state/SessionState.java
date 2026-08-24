package cl.oxman.oxmangameoptimizer.optimizer.state;

import java.util.ArrayList;
import java.util.List;

public final class SessionState {
    private final String gameName;
    private final long startedAt;
    private final List<AppliedChange> changes;

    public SessionState(String gameName, long startedAt, List<AppliedChange> changes) {
        this.gameName = gameName;
        this.startedAt = startedAt;
        this.changes = new ArrayList<>(changes);
    }

    public static SessionState begin(String gameName) {
        return new SessionState(gameName, System.currentTimeMillis(), List.of());
    }

    public String gameName() { return gameName; }
    public long startedAt() { return startedAt; }
    public List<AppliedChange> changes() { return List.copyOf(changes); }
    public void add(AppliedChange change) { changes.add(change); }
}
