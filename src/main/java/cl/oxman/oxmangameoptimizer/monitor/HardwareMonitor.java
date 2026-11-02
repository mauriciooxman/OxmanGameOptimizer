package cl.oxman.oxmangameoptimizer.monitor;

import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.Sensors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;

public class HardwareMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(HardwareMonitor.class);
    private static final Initialization INITIALIZATION = initialize();
    private static final boolean CPU_TEMPERATURE_SOURCE_IS_SPECIFIC =
            SystemInfo.getCurrentPlatform() != PlatformEnum.WINDOWS;
    private static final CpuTemperatureMonitor CPU_TEMPERATURE_MONITOR = new CpuTemperatureMonitor(
            new OshiCpuTemperatureProvider(
                    () -> hardware().getSensors().getCpuTemperature(), CPU_TEMPERATURE_SOURCE_IS_SPECIFIC),
            new LibreHardwareMonitorCpuTemperatureProvider(
                    LibreHardwareMonitorCpuTemperatureProvider.configuredExecutable()));
    private static final AtomicBoolean CPU_TEMPERATURE_DIAGNOSTIC_LOGGED = new AtomicBoolean();

    // Guarda la lectura anterior para calcular el uso de CPU
    private static long[] previousTicks = INITIALIZATION.previousTicks();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                CPU_TEMPERATURE_MONITOR::close, "hardware-monitor-shutdown"));
    }

    private static Initialization initialize() {
        try {
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            CentralProcessor processor = hardware.getProcessor();
            return new Initialization(systemInfo, hardware, processor, processor.getSystemCpuLoadTicks(), null);
        } catch (Throwable error) {
            LOG.error("HardwareMonitor initialization failed. Root cause: {}", rootCauseMessage(error), error);
            return new Initialization(null, null, null, null, error);
        }
    }

    private static SystemInfo systemInfo() {
        ensureInitialized();
        return INITIALIZATION.systemInfo();
    }

    private static HardwareAbstractionLayer hardware() {
        ensureInitialized();
        return INITIALIZATION.hardware();
    }

    private static CentralProcessor processor() {
        ensureInitialized();
        return INITIALIZATION.processor();
    }

    private static void ensureInitialized() {
        if (INITIALIZATION.failure() != null) {
            throw new IllegalStateException("HardwareMonitor initialization failed: "
                    + rootCauseMessage(INITIALIZATION.failure()), INITIALIZATION.failure());
        }
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return root.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record Initialization(SystemInfo systemInfo, HardwareAbstractionLayer hardware,
                                  CentralProcessor processor, long[] previousTicks, Throwable failure) { }

    /*
     * =========================================
     * CPU
     * =========================================
     */

    public static String getCpuName() {

        return processor()
                .getProcessorIdentifier()
                .getName();

    }

    public static synchronized double getCpuUsage() {

        double load =
                processor().getSystemCpuLoadBetweenTicks(previousTicks);

        previousTicks =
                processor().getSystemCpuLoadTicks();

        return load * 100.0;

    }

    public static int getPhysicalCores() {

        return processor().getPhysicalProcessorCount();

    }

    public static int getLogicalCores() {

        return processor().getLogicalProcessorCount();

    }

    public static double getMaxFrequencyGHz() {

        long hz = processor().getMaxFreq();

        if (hz <= 0) {

            return 0;

        }

        return hz / 1_000_000_000.0;

    }

    /**
     * Returns a validated CPU temperature when OSHI exposes a CPU-specific source.
     * <p>
     * OSHI 6.6.5 does not expose source provenance on Windows and may fall back to
     * {@code MSAcpi_ThermalZoneTemperature}, which is not necessarily the CPU/package sensor.
     * Those Windows readings are therefore rejected instead of presenting a fabricated value.
     */
    public static OptionalDouble getCpuTemperatureCelsius() {
        return CPU_TEMPERATURE_MONITOR.readCelsius();
    }

    /** Stops the optional helper without affecting the remaining hardware metrics. */
    public static void shutdownCpuTemperatureMonitor() {
        CPU_TEMPERATURE_MONITOR.close();
    }

    /** Logs OSHI's raw reading once for diagnosis without exposing it as trusted UI telemetry. */
    public static void logCpuTemperatureDiagnosticOnce() {
        if (!CPU_TEMPERATURE_DIAGNOSTIC_LOGGED.compareAndSet(false, true)) {
            return;
        }

        try {
            Sensors sensors = hardware().getSensors();
            LOG.info("OSHI temperature diagnostic provider: {}", sensors.getClass().getName());
            double rawTemperature = sensors.getCpuTemperature();
            LOG.info("OSHI cpuTemperature raw: {}", rawTemperature);
            LOG.info("OSHI cpuTemperature raw state: {}", describeRawTemperature(rawTemperature));
            LOG.info("OSHI temperature sensors exposed: aggregate getCpuTemperature() only; "
                    + "individual sensor names and source provenance are not exposed by the Sensors API");
        } catch (RuntimeException | LinkageError exception) {
            LOG.warn("OSHI cpuTemperature raw: EXCEPTION ({})", rootCauseMessage(exception));
        }
    }

    static String describeRawTemperature(double temperature) {
        if (Double.isNaN(temperature)) return "NaN";
        if (temperature == 0.0) return "ZERO";
        if (temperature < 0.0) return "NEGATIVE";
        if (!Double.isFinite(temperature)) return "POSITIVE_INFINITY";
        return "POSITIVE_FINITE";
    }

    /*
     * =========================================
     * RAM
     * =========================================
     */

    public static double getRamUsage() {

        GlobalMemory memory = hardware().getMemory();

        long total = memory.getTotal();
        long available = memory.getAvailable();

        return ((double) (total - available) / total) * 100.0;

    }

    public static double getTotalRamGB() {

        return hardware().getMemory().getTotal()
                / 1024.0 / 1024.0 / 1024.0;

    }

    public static double getUsedRamGB() {

        GlobalMemory memory = hardware().getMemory();

        long total = memory.getTotal();
        long available = memory.getAvailable();

        return (total - available)
                / 1024.0 / 1024.0 / 1024.0;

    }

    /*
     * =========================================
     * GPU
     * =========================================
     */

    public static String getGpuName() {

        if (hardware().getGraphicsCards().isEmpty()) {

            return "No detectada";

        }

        GraphicsCard gpu = hardware().getGraphicsCards().getFirst();

        return gpu.getName();

    }

    /*
     * =========================================
     * DISCO
     * =========================================
     */

    public static String getDiskName() {

        if (hardware().getDiskStores().isEmpty()) {

            return "No detectado";

        }

        return hardware()
                .getDiskStores()
                .getFirst()
                .getModel();

    }

    /*
     * =========================================
     * SISTEMA OPERATIVO
     * =========================================
     */

    public static String getOperatingSystem() {

        return systemInfo()
                .getOperatingSystem()
                .toString();

    }

    /*
     * =========================================
     * NUEVAS FUNCIONES
     * =========================================
     */

    // Memoria RAM libre
    public static double getAvailableRamGB() {

        return hardware().getMemory().getAvailable()
                / 1024.0 / 1024.0 / 1024.0;

    }

    // Memoria RAM usada en porcentaje (0-1)
    public static double getRamLoad() {

        return getRamUsage() / 100.0;

    }

    // Uso CPU (0-1)
    public static double getCpuLoad() {

        return getCpuUsage() / 100.0;

    }

    // Nombre del fabricante del CPU
    public static String getCpuVendor() {

        return processor()
                .getProcessorIdentifier()
                .getVendor();

    }

}
