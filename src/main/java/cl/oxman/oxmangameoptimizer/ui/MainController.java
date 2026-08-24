package cl.oxman.oxmangameoptimizer.ui;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.game.GameDiscoveryService;
import cl.oxman.oxmangameoptimizer.game.GamingSessionManager;
import cl.oxman.oxmangameoptimizer.monitor.HardwareMonitor;
import cl.oxman.oxmangameoptimizer.performance.PerformanceLabService;
import cl.oxman.oxmangameoptimizer.performance.PerformanceSnapshot;
import cl.oxman.oxmangameoptimizer.system.WindowsCommandRunner;
import cl.oxman.oxmangameoptimizer.system.WindowsManagedProcessLauncher;
import cl.oxman.oxmangameoptimizer.system.WindowsPrivileges;
import cl.oxman.oxmangameoptimizer.system.WindowsProcessPriorityService;
import cl.oxman.oxmangameoptimizer.performance.benchmark.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.nio.file.Path;
import java.time.Duration;

public class MainController {

    private int monitorTick;
    private final GameDiscoveryService gameDiscovery = new GameDiscoveryService();
    private final PerformanceLabService performanceLab = new PerformanceLabService(new WindowsCommandRunner());
    private final Path presentMonPath = PresentMonCapture.configuredExecutable();
    private final BenchmarkSessionCoordinator benchmarkCoordinator = new BenchmarkSessionCoordinator(
            performanceLab,
            new PresentMonCapture(presentMonPath, benchmarkDirectory(), new WindowsManagedProcessLauncher(),
                    new PresentMonCsvParser(), LogManager::addLog),
            new DefaultBenchmarkBoostService(),
            record -> BenchmarkStore.localAppData().save(record),
            this::updateBenchmarkState);
    private final ExperimentalBenchmarkCoordinator experimentalCoordinator = new ExperimentalBenchmarkCoordinator(
            performanceLab,
            new PresentMonCapture(presentMonPath, benchmarkDirectory(), new WindowsManagedProcessLauncher(),
                    new PresentMonCsvParser(), LogManager::addLog),
            new DefaultExperimentalConfigurationService(new DefaultBenchmarkBoostService(),
                    new WindowsProcessPriorityService(), LogManager::addLog),
            record -> BenchmarkStore.localAppData().save(record), this::updateBenchmarkState);
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
    @FXML private Button measureButton;
    @FXML private Label performanceLabStateLabel;
    @FXML private Label performanceLabResultLabel;
    @FXML private Label presentMonStatusLabel;
    @FXML private ComboBox<Integer> benchmarkDuration;
    @FXML private ComboBox<String> benchmarkMode;
    @FXML private ComboBox<Integer> benchmarkRuns;
    @FXML private Button benchmarkButton;
    @FXML private Button cancelBenchmarkButton;

    @FXML
    public void initialize() {
        LogManager.setLogArea(logArea);
        LogManager.addLog("Oxman elevated: " + (WindowsPrivileges.isElevated(new WindowsCommandRunner()) ? "YES" : "NO"));
        gameSelector.setCellFactory(listView -> createGameCell());
        gameSelector.setButtonCell(createGameCell());
        benchmarkDuration.getItems().setAll(30, 60, 120);
        benchmarkDuration.setValue(60);
        benchmarkMode.getItems().setAll("BOOST NORMAL", "EXPERIMENTO: PRIORIDAD");
        benchmarkMode.setValue("BOOST NORMAL");
        benchmarkRuns.getItems().setAll(1, 3, 5);
        benchmarkRuns.setValue(ExperimentalBenchmarkCoordinator.DEFAULT_RUNS);
        benchmarkRuns.disableProperty().bind(benchmarkMode.valueProperty().isEqualTo("BOOST NORMAL"));
        PresentMonDiagnostic diagnostic = PresentMonDiagnostic.inspect(presentMonPath, new WindowsCommandRunner());
        presentMonStatusLabel.setText("PresentMon: " + diagnostic.status() + " · Version: " + diagnostic.version()
                + " · Capture test: " + diagnostic.captureTest());

        hardwareExecutor.execute(this::loadHardwareInformation);
        hardwareExecutor.execute(() -> {
            boolean recovered = GamingSessionManager.recoverIncompleteSession();
            if (!recovered) Platform.runLater(() -> statusLabel.setText("Restauración pendiente; revisa el registro"));
        });
        hardwareExecutor.execute(() -> DefaultExperimentalConfigurationService.recoverIncomplete(
                new WindowsProcessPriorityService(), LogManager::addLog));
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

    @FXML
    public void measureSystemBaseline(ActionEvent event) {
        measureButton.setDisable(true);
        performanceLabStateLabel.setText("MEASURING BASELINE");
        LogManager.addLog("Performance Lab iniciado: midiendo baseline del sistema...");
        performanceLab.sampleSystem().whenComplete((snapshot, error) -> Platform.runLater(() -> {
            measureButton.setDisable(false);
            if (error != null) {
                performanceLabStateLabel.setText("FAILED");
                performanceLabResultLabel.setText("No se pudo completar la medición.");
                LogManager.addLog("Performance Lab falló: " + error.getMessage());
            } else {
                performanceLabStateLabel.setText("COMPLETED");
                performanceLabResultLabel.setText(formatSnapshot(snapshot));
                LogManager.addLog("Baseline completado con " + snapshot.sampleCount() + " muestras");
            }
        }));
    }

    @FXML
    public void startBenchmark(ActionEvent event) {
        GameProfile profile = gameSelector.getValue();
        if (profile == null) { performanceLabResultLabel.setText("Selecciona un juego."); return; }
        benchmarkButton.setDisable(true); boostButton.setDisable(true); cancelBenchmarkButton.setDisable(false);
        gameSelector.setDisable(true);
        boolean experimental = "EXPERIMENTO: PRIORIDAD".equals(benchmarkMode.getValue());
        CompletableFuture<?> future = experimental
                ? experimentalCoordinator.start(profile, Duration.ofSeconds(benchmarkDuration.getValue()), benchmarkRuns.getValue())
                : benchmarkCoordinator.start(profile, Duration.ofSeconds(benchmarkDuration.getValue()));
        future.whenComplete((outcome, error) -> Platform.runLater(() -> {
                    benchmarkButton.setDisable(false); boostButton.setDisable(false); cancelBenchmarkButton.setDisable(true);
                    gameSelector.setDisable(false);
                    if (error != null) performanceLabResultLabel.setText("Benchmark failed: " + rootMessage(error));
                    else if (outcome instanceof BenchmarkOutcome normal) performanceLabResultLabel.setText(formatOutcome(normal));
                    else performanceLabResultLabel.setText(formatExperiment((ExperimentResult) outcome));
                }));
    }

    @FXML public void cancelBenchmark(ActionEvent event) {
        benchmarkCoordinator.cancel("cancelled by user");
        experimentalCoordinator.cancel("cancelled by user");
    }

    private void updateBenchmarkState(cl.oxman.oxmangameoptimizer.performance.PerformanceLabState state, String message) {
        Platform.runLater(() -> { performanceLabStateLabel.setText(state.name()); performanceLabResultLabel.setText(message); });
        if (!message.matches("Capturando (BEFORE|BOOST): \\d+s / \\d+s")) LogManager.addLog(message);
    }

    public void shutdown() {
        hardwareExecutor.shutdownNow();
        benchmarkCoordinator.close();
        experimentalCoordinator.close();
    }

    private static Path benchmarkDirectory() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return base.resolve("OxmanGameOptimizer").resolve("benchmarks");
    }

    private static String formatOutcome(BenchmarkOutcome outcome) {
        var before = outcome.record().before(); var after = outcome.record().after();
        var systemBefore = outcome.record().systemBefore(); var systemAfter = outcome.record().systemAfter();
        return String.format("FPS %s → %s | 1%% Low %s → %s | Frame time %s → %s ms%n"
                        + "CPU %.1f%% → %.1f%% | RAM %.1f → %.1f GB | Procesos %.0f → %.0f | Power %s → %s%n"
                        + "%s%nResults can vary with gameplay conditions. Repeat the same scenario for higher confidence.",
                metric(before.averageFps()), metric(after.averageFps()), metric(before.onePercentLow()), metric(after.onePercentLow()),
                metric(before.averageFrameTimeMs()), metric(after.averageFrameTimeMs()),
                systemBefore.cpuAverage(), systemAfter.cpuAverage(), systemBefore.ramUsedAverage(), systemAfter.ramUsedAverage(),
                systemBefore.processCountAverage(), systemAfter.processCountAverage(), systemBefore.activePowerPlan(), systemAfter.activePowerPlan(),
                outcome.interpretation().message());
    }
    private static String metric(java.util.OptionalDouble value) { return value.isPresent() ? String.format("%.1f", value.getAsDouble()) : "N/A"; }
    private static String formatExperiment(ExperimentResult value) {
        return String.format("PROCESS PRIORITY EXPERIMENT%nSAFE vs ABOVE NORMAL · Runs: %d%n"
                        + "Avg FPS %s vs %s | 1%% Low %s vs %s | Frame time %s vs %s ms%n%s%n"
                        + "Priority change: NORMAL → ABOVE_NORMAL (restaurada después de cada captura)",
                value.runs().size(), metric(value.safe().averageFps().mean()), metric(value.aboveNormal().averageFps().mean()),
                metric(value.safe().onePercentLow().mean()), metric(value.aboveNormal().onePercentLow().mean()),
                metric(value.safe().frameTime().mean()), metric(value.aboveNormal().frameTime().mean()),
                value.interpretation().message());
    }
    private static String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage(); }

    private static String formatSnapshot(PerformanceSnapshot snapshot) {
        return String.format("CPU %.1f%% (%.1f–%.1f)  |  RAM %.1f GB  |  Procesos %.0f  |  %s",
                snapshot.cpuAverage(), snapshot.cpuMinimum(), snapshot.cpuMaximum(),
                snapshot.ramUsedAverage(), snapshot.processCountAverage(), snapshot.activePowerPlan());
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
                setGraphic(null);
            }
        };
    }
}
