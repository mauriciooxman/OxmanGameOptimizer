package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.monitor.HardwareMonitor;
import cl.oxman.oxmangameoptimizer.optimizer.BoostOptimizer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.util.Duration;

public class MainController {

    private boolean boosting = false;

    @FXML
    private Label cpuLabel;

    @FXML
    private Label ramLabel;

    @FXML
    private Label gpuLabel;

    @FXML
    private Label diskLabel;

    @FXML
    private Label osLabel;

    @FXML
    private Label cpuNameLabel;

    @FXML
    private Label cpuCoresLabel;

    @FXML
    private Label cpuFreqLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressBar cpuBar;

    @FXML
    private ProgressBar ramBar;

    @FXML
    private TextArea logArea;

    @FXML
    public void initialize() {

        // Conectar sistema de logs
        LogManager.setLogArea(logArea);

        // Información fija del hardware
        gpuLabel.setText(HardwareMonitor.getGpuName());
        diskLabel.setText(HardwareMonitor.getDiskName());
        osLabel.setText(HardwareMonitor.getOperatingSystem());

        cpuNameLabel.setText(HardwareMonitor.getCpuName());

        cpuCoresLabel.setText(
                HardwareMonitor.getPhysicalCores()
                        + " Núcleos | "
                        + HardwareMonitor.getLogicalCores()
                        + " Hilos"
        );

        cpuFreqLabel.setText(
                String.format(
                        "Frecuencia Máx: %.2f GHz",
                        HardwareMonitor.getMaxFrequencyGHz()
                )
        );

        Timeline timeline = new Timeline(

                new KeyFrame(
                        Duration.millis(500),
                        event -> updateHardware()
                )

        );

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

    }

    /**
     * Actualiza CPU, RAM y estado.
     */
    private void updateHardware() {

        //------------------------
        // CPU
        //------------------------

        double cpu = HardwareMonitor.getCpuUsage();

        cpuLabel.setText(
                String.format("CPU %.1f %%", cpu)
        );

        cpuBar.setProgress(cpu / 100.0);

        //------------------------
        // RAM
        //------------------------

        double ram = HardwareMonitor.getRamUsage();

        ramLabel.setText(

                String.format(
                        "RAM %.1f%% (%.1f / %.1f GB)",
                        ram,
                        HardwareMonitor.getUsedRamGB(),
                        HardwareMonitor.getTotalRamGB()
                )

        );

        ramBar.setProgress(ram / 100.0);

        //------------------------
        // Estado
        //------------------------

        if (boosting) {
            return;
        }

        if (cpu < 40) {

            statusLabel.setText("🟢 Sistema Óptimo");

        } else if (cpu < 75) {

            statusLabel.setText("🟡 Carga Moderada");

        } else {

            statusLabel.setText("🔴 Alta utilización");

        }

    }

    @FXML
    public void boostFPS(ActionEvent event) {

        boosting = true;

        statusLabel.setText("🚀 Optimizando...");

        logArea.clear();

        new Thread(() -> {

            BoostOptimizer.applyBoost();

            Platform.runLater(() -> {

                statusLabel.setText("🚀 Sistema optimizado");

                boosting = false;

            });

        }).start();

    }

}