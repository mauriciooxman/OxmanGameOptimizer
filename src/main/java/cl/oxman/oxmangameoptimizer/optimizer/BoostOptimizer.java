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

            LogManager.addLog("🗑 Liberando memoria...");
            System.gc();
            Thread.sleep(700);
            LogManager.addLog("✔ Memoria liberada");

            LogManager.addLog("");
            LogManager.addLog("🎉 Optimización completada.");

        } catch (InterruptedException e) {

            e.printStackTrace();

        }

    }

}