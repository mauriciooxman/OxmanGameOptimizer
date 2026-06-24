package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

import java.io.IOException;

public class WindowsOptimizer {

    public static void optimize() {

        try {

            LogManager.addLog("🪟 Limpiando caché de miniaturas...");

            ProcessBuilder pb = new ProcessBuilder(
                    "cmd",
                    "/c",
                    "ie4uinit.exe -ClearIconCache"
            );

            pb.start();

            LogManager.addLog("✔ Caché de iconos limpiada");

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

}