package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.monitor.HardwareMonitor;
import cl.oxman.oxmangameoptimizer.optimizer.BoostOptimizer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
    private Label statusLabel;

    @FXML
    private ProgressBar cpuBar;

    @FXML
    private ProgressBar ramBar;

    @FXML
    private TextArea logArea;

    @FXML
    public void initialize() {

        // Conectar el sistema de logs
        LogManager.setLogArea(logArea);

        // Información del hardware
        System.out.println("CPU: " + HardwareMonitor.getCpuName());
        System.out.printf("RAM Total: %.2f GB%n", HardwareMonitor.getTotalRamGB());

        Timeline timeline = new Timeline(

                new KeyFrame(Duration.millis(500), event -> {

                    //-------------------
                    // CPU
                    //-------------------

                    double cpu = HardwareMonitor.getCpuUsage();

                    cpuLabel.setText(
                            String.format("CPU %.1f %%", cpu)
                    );

                    cpuBar.setProgress(cpu / 100.0);

                    //-------------------
                    // RAM
                    //-------------------

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

                    //-------------------
                    // Estado
                    //-------------------

                    if (!boosting) {

                        if (cpu < 40) {

                            statusLabel.setText("🟢 Sistema Óptimo");

                        }
                        else if (cpu < 75) {

                            statusLabel.setText("🟡 Carga Moderada");

                        }
                        else {

                            statusLabel.setText("🔴 Alta utilización");

                        }

                    }

                })

        );

        timeline.setCycleCount(Animation.INDEFINITE);

        timeline.play();

    }

    @FXML
    public void boostFPS(ActionEvent event) {

        boosting = true;

        statusLabel.setText("🚀 Optimizando...");

        logArea.clear();

        new Thread(() -> {

            BoostOptimizer.applyBoost();

            javafx.application.Platform.runLater(() -> {

                statusLabel.setText("🚀 Sistema optimizado");

                boosting = false;

            });

        }).start();

    }
}