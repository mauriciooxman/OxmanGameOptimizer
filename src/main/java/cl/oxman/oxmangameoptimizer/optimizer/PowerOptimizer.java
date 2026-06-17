package cl.oxman.oxmangameoptimizer.optimizer;

import java.io.IOException;

public class PowerOptimizer {

    public static void enableHighPerformance() {

        try {

            ProcessBuilder pb = new ProcessBuilder(

                    "cmd",
                    "/c",
                    "powercfg /setactive SCHEME_MIN"

            );

            pb.start();

        }

        catch (IOException e) {

            e.printStackTrace();

        }

    }

}