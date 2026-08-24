package cl.oxman.oxmangameoptimizer.monitor;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardwareMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(HardwareMonitor.class);
    private static final Initialization INITIALIZATION = initialize();

    // Guarda la lectura anterior para calcular el uso de CPU
    private static long[] previousTicks = INITIALIZATION.previousTicks();

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
