package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

public class CpuOptimizer {

    public static void optimize() {

        LogManager.addLog("🖥 Optimizando CPU...");

        try {

            // Pequeña pausa para simular el proceso
            Thread.sleep(500);

            LogManager.addLog("✔ Procesos del sistema estabilizados");

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        }

    }

}