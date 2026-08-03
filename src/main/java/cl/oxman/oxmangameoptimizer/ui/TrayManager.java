package cl.oxman.oxmangameoptimizer.ui;

import javafx.application.Platform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;

public final class TrayManager {

    private static TrayIcon trayIcon;
    private static Stage stage;
    private static Runnable beforeExit;

    private TrayManager() {
    }

    public static void initialize(Stage applicationStage, Runnable exitAction) {
        stage = applicationStage;
        beforeExit = exitAction;

        if (!SystemTray.isSupported()) {
            return;
        }

        try {
            java.awt.Image image = ImageIO.read(TrayManager.class.getResource(
                    "/cl/oxman/oxmangameoptimizer/company-logo-gold.png"));

            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem("Abrir Oxman Game Optimizer");
            openItem.addActionListener(event -> restoreWindow());
            MenuItem exitItem = new MenuItem("Salir");
            exitItem.addActionListener(event -> exitApplication());
            menu.add(openItem);
            menu.addSeparator();
            menu.add(exitItem);

            trayIcon = new TrayIcon(image, "Oxman Game Optimizer", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(event -> restoreWindow());
            SystemTray.getSystemTray().add(trayIcon);

            stage.iconifiedProperty().addListener((observable, oldValue, minimized) -> {
                if (minimized) {
                    Platform.runLater(() -> {
                        stage.setIconified(false);
                        stage.hide();
                        trayIcon.displayMessage(
                                "Oxman Game Optimizer",
                                "La optimización continúa en segundo plano.",
                                TrayIcon.MessageType.INFO
                        );
                    });
                }
            });
        } catch (IOException | AWTException e) {
            trayIcon = null;
        }
    }

    public static void exitApplication() {
        Thread exitThread = new Thread(() -> {
            if (beforeExit != null) {
                beforeExit.run();
            }
            if (trayIcon != null) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
            Platform.runLater(Platform::exit);
        }, "application-exit");
        exitThread.setDaemon(false);
        exitThread.start();
    }

    private static void restoreWindow() {
        Platform.runLater(() -> {
            stage.show();
            stage.toFront();
            stage.requestFocus();
        });
    }
}
