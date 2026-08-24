package cl.oxman.oxmangameoptimizer.system;

import java.time.Duration;
import java.util.List;

public final class WindowsPrivileges {
    private WindowsPrivileges() { }

    public static boolean isElevated(SystemCommandRunner runner) {
        CommandResult result = runner.run(List.of("whoami", "/groups"), Duration.ofSeconds(5));
        if (!result.succeeded()) return false;
        String groups = result.output().toUpperCase();
        return groups.contains("S-1-16-12288") || groups.contains("S-1-16-16384");
    }
}
