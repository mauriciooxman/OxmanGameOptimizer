package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.game.GameDiscoveryService;
import cl.oxman.oxmangameoptimizer.game.GamingSessionManager;
import cl.oxman.oxmangameoptimizer.monitor.HardwareMonitor;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class MainController {

    private int monitorTick;
    private final GameDiscoveryService gameDiscovery = new GameDiscoveryService();
    private volatile List<GameProfile> detectedGames = List.of();
    private String lastRunningGameId;
    private final ScheduledExecutorService hardwareExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "hardware-monitor");
                thread.setDaemon(true);
                return thread;
            });

    @FXML private Label cpuLabel;
    @FXML private Label ramLabel;
    @FXML private Label gpuLabel;
    @FXML private Label diskLabel;
    @FXML private Label osLabel;
    @FXML private Label cpuNameLabel;
    @FXML private Label cpuCoresLabel;
    @FXML private Label cpuFreqLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar cpuBar;
    @FXML private ProgressBar ramBar;
    @FXML private TextArea logArea;
    @FXML private ComboBox<GameProfile> gameSelector;
    @FXML private Button boostButton;
    @FXML private Button finishButton;

    @FXML
    public void initialize() {
        LogManager.setLogArea(logArea);
        gameSelector.setCellFactory(listView -> createGameCell());
        gameSelector.setButtonCell(createGameCell());

        hardwareExecutor.execute(this::loadHardwareInformation);
        hardwareExecutor.execute(this::discoverGames);
        hardwareExecutor.scheduleAtFixedRate(
                this::readHardwareUsage, 500, 1000, TimeUnit.MILLISECONDS);
        hardwareExecutor.scheduleAtFixedRate(this::detectRunningGame, 2, 2, TimeUnit.SECONDS);
    }

    private void discoverGames() {
        detectedGames = gameDiscovery.discoverInstalledGames();
        Platform.runLater(() -> {
            gameSelector.getItems().setAll(detectedGames);
            if (detectedGames.isEmpty()) {
                gameSelector.setPromptText("No se encontraron juegos");
                statusLabel.setText("No se encontraron juegos instalados");
                LogManager.addLog("ℹ No se encontraron instalaciones de juegos compatibles.");
            } else {
                gameSelector.setPromptText("Selecciona o abre un juego");
                statusLabel.setText("Se detectaron " + detectedGames.size() + " juegos instalados");
                LogManager.addLog("🎮 " + detectedGames.size() + " juegos instalados encontrados.");
            }
        });
    }

    private void detectRunningGame() {
        if (GamingSessionManager.isSessionActive() || detectedGames.isEmpty()) return;
        gameDiscovery.findRunningGame(detectedGames).ifPresentOrElse(profile -> {
            if (profile.getId().equals(lastRunningGameId)) return;
            lastRunningGameId = profile.getId();
            Platform.runLater(() -> {
                gameSelector.getSelectionModel().select(profile);
                statusLabel.setText("🎮 Detectado: " + profile);
                LogManager.addLog("✔ Juego abierto detectado automáticamente: " + profile);
            });
        }, () -> lastRunningGameId = null);
    }

    private void loadHardwareInformation() {
        try {
            String gpu = HardwareMonitor.getGpuName();
            String disk = HardwareMonitor.getDiskName();
            String os = HardwareMonitor.getOperatingSystem();
            String cpuName = HardwareMonitor.getCpuName();
            String cores = HardwareMonitor.getPhysicalCores()
                    + " Núcleos | " + HardwareMonitor.getLogicalCores() + " Hilos";
            String frequency = String.format(
                    "Frecuencia Máx: %.2f GHz", HardwareMonitor.getMaxFrequencyGHz());

            Platform.runLater(() -> {
                gpuLabel.setText(gpu);
                diskLabel.setText(disk);
                osLabel.setText(os);
                cpuNameLabel.setText(cpuName);
                cpuCoresLabel.setText(cores);
                cpuFreqLabel.setText(frequency);
            });
        } catch (RuntimeException exception) {
            Platform.runLater(() -> statusLabel.setText(
                    "No se pudo leer toda la información del hardware"));
        }
    }

    private void readHardwareUsage() {
        monitorTick++;
        if (GamingSessionManager.isSessionActive() && monitorTick % 4 != 0) {
            return;
        }

        try {
            double cpu = HardwareMonitor.getCpuUsage();
            double ram = HardwareMonitor.getRamUsage();
            double usedRam = HardwareMonitor.getUsedRamGB();
            double totalRam = HardwareMonitor.getTotalRamGB();

            Platform.runLater(() -> updateHardwareControls(
                    cpu, ram, usedRam, totalRam));
        } catch (RuntimeException exception) {
            Platform.runLater(() -> statusLabel.setText(
                    "No se pudo actualizar el monitor de hardware"));
        }
    }

    private void updateHardwareControls(
            double cpu, double ram, double usedRam, double totalRam) {
        cpuLabel.setText(String.format("CPU %.1f %%", cpu));
        cpuBar.setProgress(cpu / 100.0);
        ramLabel.setText(String.format("RAM %.1f%% (%.1f / %.1f GB)",
                ram, usedRam, totalRam));
        ramBar.setProgress(ram / 100.0);

        if (GamingSessionManager.isSessionActive()) return;

        if (cpu < 40) {
            statusLabel.setText("🟢 Sistema óptimo");
        } else if (cpu < 75) {
            statusLabel.setText("🟡 Carga moderada");
        } else {
            statusLabel.setText("🔴 Alta utilización");
        }
    }

    @FXML
    public void boostFPS(ActionEvent event) {
        GameProfile profile = gameSelector.getValue();
        if (profile == null) {
            statusLabel.setText("Selecciona un juego");
            return;
        }

        logArea.clear();
        if (GamingSessionManager.start(profile, this::updateSessionStatus)) {
            boostButton.setDisable(true);
            finishButton.setDisable(false);
            gameSelector.setDisable(true);
        }
    }

    @FXML
    public void finishGame(ActionEvent event) {
        finishButton.setDisable(true);
        GamingSessionManager.finishManually(this::updateSessionStatus);
    }

    private void updateSessionStatus(String status) {
        Platform.runLater(() -> {
            statusLabel.setText(status);
            if ("Windows restaurado".equals(status)) {
                boostButton.setDisable(false);
                finishButton.setDisable(true);
                gameSelector.setDisable(false);
            }
        });
    }

    private ListCell<GameProfile> createGameCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(GameProfile profile, boolean empty) {
                super.updateItem(profile, empty);
                if (empty || profile == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                setText(profile.toString());
                var stream = profile.getIconResource() == null ? null
                        : MainController.class.getResourceAsStream(profile.getIconResource());
                if (stream == null) {
                    setGraphic(null);
                } else {
                    ImageView icon = new ImageView(new Image(stream));
                    icon.setFitWidth(26);
                    icon.setFitHeight(26);
                    icon.setPreserveRatio(true);
                    icon.setSmooth(true);
                    setGraphic(icon);
                }
            }
        };
    }
}
