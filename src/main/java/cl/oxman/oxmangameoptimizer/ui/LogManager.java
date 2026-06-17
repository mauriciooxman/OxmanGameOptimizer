package cl.oxman.oxmangameoptimizer.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class LogManager {

    private static TextArea logArea;

    public static void setLogArea(TextArea area) {
        logArea = area;
    }

    public static void addLog(String text) {

        if (logArea == null) {
            return;
        }

        Platform.runLater(() ->
                logArea.appendText(text + "\n")
        );

    }

}