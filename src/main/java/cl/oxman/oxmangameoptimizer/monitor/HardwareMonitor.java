package cl.oxman.oxmangameoptimizer.monitor;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

public class HardwareMonitor {

    private static final SystemInfo systemInfo = new SystemInfo();

    private static final HardwareAbstractionLayer hardware =
            systemInfo.getHardware();

    private static final CentralProcessor processor =
            hardware.getProcessor();

    // Guarda la lectura anterior de la CPU
    private static long[] previousTicks =
            processor.getSystemCpuLoadTicks();

    /*
     * Devuelve el porcentaje de uso de RAM
     */
    public static double getRamUsage() {

        GlobalMemory memory = hardware.getMemory();

        long total = memory.getTotal();
        long available = memory.getAvailable();

        return ((double) (total - available) / total) * 100.0;
    }

    /*
     * Devuelve la RAM total instalada en GB
     */
    public static double getTotalRamGB() {

        return hardware.getMemory().getTotal()
                / 1024.0 / 1024.0 / 1024.0;
    }

    /*
     * Devuelve la RAM utilizada en GB
     */
    public static double getUsedRamGB() {

        GlobalMemory memory = hardware.getMemory();

        long total = memory.getTotal();
        long available = memory.getAvailable();

        return (total - available)
                / 1024.0 / 1024.0 / 1024.0;
    }

    /*
     * Devuelve el nombre del procesador
     */
    public static String getCpuName() {

        return processor
                .getProcessorIdentifier()
                .getName();
    }

    /*
     * Devuelve el porcentaje de uso de CPU
     */
    public static double getCpuUsage() {

        double load =
                processor.getSystemCpuLoadBetweenTicks(previousTicks);

        previousTicks =
                processor.getSystemCpuLoadTicks();

        return load * 100.0;
    }

}