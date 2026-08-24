package cl.oxman.oxmangameoptimizer.system;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class WindowsCommandRunner implements SystemCommandRunner {
    @Override
    public CommandResult run(List<String> command, Duration timeout) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Process running = process;
            Thread outputReader = Thread.ofVirtual().start(() -> {
                try { running.getInputStream().transferTo(stdout); }
                catch (IOException ignored) { }
            });
            Thread errorReader = Thread.ofVirtual().start(() -> {
                try { running.getErrorStream().transferTo(stderr); }
                catch (IOException ignored) { }
            });
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                outputReader.join(); errorReader.join();
                return new CommandResult(-1, stdout.toString(Charset.defaultCharset()), stderr.toString(Charset.defaultCharset()), true);
            }
            outputReader.join(); errorReader.join();
            return new CommandResult(process.exitValue(), stdout.toString(Charset.defaultCharset()), stderr.toString(Charset.defaultCharset()), false);
        } catch (IOException exception) {
            return new CommandResult(-1, exception.getMessage() == null ? "" : exception.getMessage(), false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return new CommandResult(-1, "interrupted", false);
        }
    }
}
