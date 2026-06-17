package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

public class CpuOptimizer {

    public static void optimize() {

        LogManager.addLog("🖥 Aplicando optimización de CPU...");

        System.gc();

        LogManager.addLog("✅ Optimización CPU completada.");

    }

}