package cl.oxman.oxmangameoptimizer.monitor;

import cl.oxman.oxmangameoptimizer.system.CommandResult;
import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public record SessionMetrics(double cpuPercent, double usedRamGb, double availableRamGb,
                             long processCount, String powerPlan) {
    private static final Pattern PLAN_NAME = Pattern.compile("\\(([^)]+)\\)");

    public static SessionMetrics capture(SystemCommandRunner commands) {
        double cpu = HardwareMonitor.getCpuUsage();
        double used = HardwareMonitor.getUsedRamGB();
        double available = HardwareMonitor.getAvailableRamGB();
        long processes = ProcessHandle.allProcesses().count();
        CommandResult result = commands.run(List.of("powercfg", "/getactivescheme"), Duration.ofSeconds(5));
        String plan = "No disponible";
        if (result.succeeded()) {
            var matcher = PLAN_NAME.matcher(result.output());
            if (matcher.find()) plan = matcher.group(1).trim();
        }
        return new SessionMetrics(cpu, used, available, processes, plan);
    }

    public String comparison(SessionMetrics after) {
        return String.format("Procesos: %d -> %d | RAM usada: %.1f -> %.1f GB | "
                        + "CPU background: %.1f%% -> %.1f%% | Power plan: %s -> %s",
                processCount, after.processCount, usedRamGb, after.usedRamGb,
                cpuPercent, after.cpuPercent, powerPlan, after.powerPlan);
    }
}
