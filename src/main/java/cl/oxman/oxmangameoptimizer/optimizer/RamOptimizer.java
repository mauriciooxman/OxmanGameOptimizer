package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

public class RamOptimizer {

    public static void optimize() {

        LogManager.addLog("💾 Liberando memoria RAM...");

        System.gc();

        LogManager.addLog("✔ Memoria optimizada");

    }

}