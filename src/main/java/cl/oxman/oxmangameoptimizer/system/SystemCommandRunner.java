package cl.oxman.oxmangameoptimizer.system;

import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface SystemCommandRunner {
    CommandResult run(List<String> command, Duration timeout);
}
