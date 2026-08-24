package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;

public record BackgroundProcessObservation(ProcessIdentity identity, String user,
        boolean currentUser, boolean foreground, double cpuAveragePercent) { }
