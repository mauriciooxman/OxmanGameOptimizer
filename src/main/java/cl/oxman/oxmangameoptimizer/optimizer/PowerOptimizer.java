package cl.oxman.oxmangameoptimizer.optimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PowerOptimizer {

    private static final Pattern GUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static String originalSchemeGuid;

    private PowerOptimizer() {
    }

    public static synchronized boolean enableHighPerformance() {
        if (originalSchemeGuid == null) {
            originalSchemeGuid = getActiveSchemeGuid();
        }
        if (originalSchemeGuid == null) {
            return false;
        }
        return runPowerCfg("/setactive", "SCHEME_MIN");
    }

    public static synchronized boolean restoreOriginalPlan() {
        if (originalSchemeGuid == null) {
            return true;
        }

        boolean restored = runPowerCfg("/setactive", originalSchemeGuid);
        if (restored) {
            originalSchemeGuid = null;
        }
        return restored;
    }

    private static String getActiveSchemeGuid() {
        try {
            Process process = new ProcessBuilder("powercfg", "/getactivescheme")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), Charset.defaultCharset()))) {
                reader.lines().forEach(output::append);
            }
            if (process.waitFor() == 0) {
                Matcher matcher = GUID_PATTERN.matcher(output);
                return matcher.find() ? matcher.group() : null;
            }
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private static boolean runPowerCfg(String... arguments) {
        String[] command = new String[arguments.length + 1];
        command[0] = "powercfg";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        try {
            return new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
