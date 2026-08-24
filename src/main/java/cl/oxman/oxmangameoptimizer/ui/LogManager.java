package cl.oxman.oxmangameoptimizer.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class LogManager {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<String> clientEntries = new ArrayList<>();
    private static final List<String> technicalEntries = new ArrayList<>();
    private static TextArea logArea;
    private static ApplicationMode mode = ApplicationMode.CLIENT;

    private LogManager() { }

    public static synchronized void setLogArea(TextArea area) {
        logArea = area;
        refresh();
    }

    public static void addLog(String text) { append(text, LogAudience.TECHNICAL); }

    public static void addClientLog(String text) { append(text, LogAudience.CLIENT); }

    public static synchronized void setMode(ApplicationMode applicationMode) {
        mode = applicationMode == null ? ApplicationMode.CLIENT : applicationMode;
        refresh();
    }

    public static synchronized ApplicationMode mode() { return mode; }

    public static synchronized void clear() {
        clientEntries.clear();
        technicalEntries.clear();
        if (logArea != null) Platform.runLater(logArea::clear);
    }

    static boolean visibleInClientMode(String text, LogAudience audience) {
        if (audience == LogAudience.CLIENT) return true;
        String normalized = text == null ? "" : text.toLowerCase();
        return normalized.contains("❌") || normalized.contains("falló")
                || normalized.contains("error") || normalized.contains("incompleta");
    }

    static synchronized void resetForTests() {
        clientEntries.clear();
        technicalEntries.clear();
        mode = ApplicationMode.CLIENT;
        logArea = null;
    }

    static synchronized List<String> entriesFor(ApplicationMode applicationMode) {
        return List.copyOf(applicationMode == ApplicationMode.ADVANCED ? technicalEntries : clientEntries);
    }

    private static synchronized void append(String text, LogAudience audience) {
        String entry = "[" + LocalTime.now().format(TIME) + "] " + text;
        technicalEntries.add(entry);
        boolean clientVisible = visibleInClientMode(text, audience);
        if (clientVisible) clientEntries.add(entry);
        if (logArea != null && (mode == ApplicationMode.ADVANCED || clientVisible)) {
            Platform.runLater(() -> logArea.appendText(entry + "\n"));
        }
    }

    private static void refresh() {
        if (logArea == null) return;
        List<String> source = mode == ApplicationMode.ADVANCED ? technicalEntries : clientEntries;
        String content = source.isEmpty() ? "" : String.join("\n", source) + "\n";
        Platform.runLater(() -> logArea.setText(content));
    }
}
