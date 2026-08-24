package cl.oxman.oxmangameoptimizer.system;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface ManagedProcessLauncher { ManagedProcess start(List<String> command, Path workingDirectory) throws IOException; }
