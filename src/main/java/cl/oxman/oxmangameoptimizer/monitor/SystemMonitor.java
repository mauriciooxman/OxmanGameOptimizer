package cl.oxman.oxmangameoptimizer.monitor;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class SystemMonitor {

    private static final OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    public static double getRamUsage() {

        long total = os.getTotalMemorySize();
        long free = os.getFreeMemorySize();

        return ((double)(total - free) / total) * 100;
    }
}
