package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

public class BoostOptimizer {

    public static void applyBoost() {

        try {

            LogManager.addLog("🧹 Limpiando archivos temporales...");
            TempOptimizer.cleanTemp();
            Thread.sleep(700);
            LogManager.addLog("✔ Temporales eliminados");

            LogManager.addLog("⚡ Activando Alto Rendimiento...");
            PowerOptimizer.enableHighPerformance();
            Thread.sleep(700);
            LogManager.addLog("✔ Plan de energía activado");

            LogManager.addLog("🌐 Limpiando caché DNS...");
            NetworkOptimizer.flushDNS();
            Thread.sleep(700);
            LogManager.addLog("✔ Caché DNS limpiada");

            CpuOptimizer.optimize();
            Thread.sleep(500);

            RamOptimizer.optimize();
            Thread.sleep(500);

            ServiceOptimizer.optimize();
            Thread.sleep(500);

            WindowsOptimizer.optimize();
            Thread.sleep(500);

            LogManager.addLog("");
            LogManager.addLog("🎉 Optimización completada.");

        } catch (InterruptedException e) {

            e.printStackTrace();

        }

    }

}