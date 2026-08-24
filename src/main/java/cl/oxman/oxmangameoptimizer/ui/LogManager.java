package cl.oxman.oxmangameoptimizer.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LogManager {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static TextArea logArea;

    public static void setLogArea(TextArea area) {
        logArea = area;
    }

    public static void addLog(String text) {

        if (logArea == null) {
            return;
        }

        String entry = "[" + LocalTime.now().format(TIME) + "] " + text;
        Platform.runLater(() ->
                logArea.appendText(entry + "\n")
        );

    }

}
