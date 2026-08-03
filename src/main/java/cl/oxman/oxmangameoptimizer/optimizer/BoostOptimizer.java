package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

public final class BoostOptimizer {

    private BoostOptimizer() {
    }

    public static void applyBoost(String gameName) {
        LogManager.addLog("🚀 Preparando perfil competitivo para " + gameName + "...");

        LogManager.addLog("⚡ Guardando plan de energía actual...");
        if (PowerOptimizer.enableHighPerformance()) {
            LogManager.addLog("✔ Plan de alto rendimiento activado");
        } else {
            LogManager.addLog("⚠ No se pudo activar alto rendimiento");
        }

        ServiceOptimizer.optimize();

        LogManager.addLog("✔ Se conservaron SysMain, audio, red, Steam y Vanguard");
        LogManager.addLog("✔ Sin limpieza de cachés que pueda provocar tirones");
        LogManager.addLog("");
        LogManager.addLog("🎉 Perfil competitivo aplicado.");
    }

    public static void restoreDefaults() {
        LogManager.addLog("🎮 Finalizando sesión de juego...");

        LogManager.addLog("⚡ Restaurando el plan de energía original...");
        if (PowerOptimizer.restoreOriginalPlan()) {
            LogManager.addLog("✔ Plan de energía original restaurado");
        } else {
            LogManager.addLog("⚠ No se pudo restaurar el plan de energía");
        }

        LogManager.addLog("⚙ Reactivando servicios detenidos por Oxman...");
        ServiceOptimizer.restoreServices();

        LogManager.addLog("✔ CPU, memoria y red permanecen administradas por Windows");
        LogManager.addLog("");
        LogManager.addLog("✅ Windows restaurado para uso normal.");
    }
}
