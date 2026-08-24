package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

/** @deprecated Windows schedules CPU work; this legacy hook intentionally performs no tweak. */
@Deprecated(forRemoval = false)
public class CpuOptimizer {

    public static void optimize() {

        LogManager.addLog("ℹ CPU: sin tweaks; scheduling administrado por Windows");

    }

}
