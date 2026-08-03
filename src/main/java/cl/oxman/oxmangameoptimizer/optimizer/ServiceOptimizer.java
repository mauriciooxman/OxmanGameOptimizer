package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.ui.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServiceOptimizer {

    private static final String[] SERVICES = {
            "DiagTrack",
            "WSearch"
    };

    private static final int SERVICE_RUNNING = 4;
    private static final Pattern SERVICE_STATE = Pattern.compile(":\\s*(\\d+)");

    // Solo se restauran servicios que esta ejecución encontró activos y detuvo.
    private static final Set<String> STOPPED_BY_OPTIMIZER =
            Collections.synchronizedSet(new LinkedHashSet<>());

    private ServiceOptimizer() {
    }

    public static void optimize() {
        LogManager.addLog("⚙ Deteniendo servicios no esenciales...");

        for (String service : SERVICES) {
            int initialState = getServiceState(service);

            if (initialState == 1) {
                LogManager.addLog("ℹ Servicio ya detenido, se conserva: " + service);
                continue;
            }

            if (initialState < 0) {
                LogManager.addLog("⚠ No se pudo consultar el servicio: " + service);
                continue;
            }

            if (runServiceCommand("stop", service)) {
                STOPPED_BY_OPTIMIZER.add(service);
                LogManager.addLog("✔ Servicio detenido: " + service);
            } else {
                LogManager.addLog("⚠ No se pudo detener: " + service);
            }
        }
    }

    /**
     * Inicia únicamente los servicios que fueron detenidos por {@link #optimize()}.
     */
    public static void restoreServices() {
        String[] servicesToRestore;

        synchronized (STOPPED_BY_OPTIMIZER) {
            servicesToRestore = STOPPED_BY_OPTIMIZER.toArray(String[]::new);
        }

        if (servicesToRestore.length == 0) {
            LogManager.addLog("ℹ No hay servicios para restaurar.");
            return;
        }

        LogManager.addLog("⚙ Restaurando servicios...");

        for (String service : servicesToRestore) {
            boolean restored = getServiceState(service) == SERVICE_RUNNING
                    || runServiceCommand("start", service);

            if (restored) {
                STOPPED_BY_OPTIMIZER.remove(service);
                LogManager.addLog("✔ Servicio restaurado: " + service);
            } else {
                LogManager.addLog("⚠ No se pudo restaurar: " + service);
            }
        }
    }

    private static int getServiceState(String serviceName) {
        CommandResult result = runCommand("sc", "query", serviceName);

        if (result.exitCode() != 0) {
            return -1;
        }

        for (String line : result.output().lines().toList()) {
            Matcher matcher = SERVICE_STATE.matcher(line);
            if (matcher.find()) {
                int state = Integer.parseInt(matcher.group(1));
                if (state >= 1 && state <= 7) {
                    return state;
                }
            }
        }

        return -1;
    }

    private static boolean runServiceCommand(String action, String serviceName) {
        return runCommand("sc", action, serviceName).exitCode() == 0;
    }

    private static CommandResult runCommand(String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            return new CommandResult(process.waitFor(), output.toString());
        } catch (IOException e) {
            LogManager.addLog("❌ Error ejecutando el comando de servicios.");
            return new CommandResult(-1, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LogManager.addLog("❌ Operación de servicios interrumpida.");
            return new CommandResult(-1, "");
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
