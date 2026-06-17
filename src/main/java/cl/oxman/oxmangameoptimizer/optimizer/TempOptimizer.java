package cl.oxman.oxmangameoptimizer.optimizer;

import java.io.File;

public class TempOptimizer {

    public static void cleanTemp() {

        String temp = System.getenv("TEMP");

        deleteFolder(new File(temp));

    }

    private static void deleteFolder(File folder) {

        File[] files = folder.listFiles();

        if (files == null)
            return;

        for (File file : files) {

            try {

                if (file.isDirectory()) {

                    deleteFolder(file);

                }

                file.delete();

            }

            catch (Exception ignored) {

            }

        }

    }

}