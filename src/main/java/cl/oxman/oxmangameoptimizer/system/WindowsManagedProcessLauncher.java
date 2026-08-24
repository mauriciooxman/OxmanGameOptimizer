package cl.oxman.oxmangameoptimizer.system;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public final class WindowsManagedProcessLauncher implements ManagedProcessLauncher {
    public ManagedProcess start(List<String> command, Path workingDirectory) throws IOException {
        Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stdoutReader = Thread.ofVirtual().start(() -> transfer(process.getInputStream(), stdout));
        Thread stderrReader = Thread.ofVirtual().start(() -> transfer(process.getErrorStream(), stderr));
        return new ManagedProcess() {
            public boolean waitFor(Duration timeout) throws InterruptedException { return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            public int exitCode() { return process.exitValue(); }
            public String stdout() throws InterruptedException { stdoutReader.join(); return stdout.toString(Charset.defaultCharset()); }
            public String stderr() throws InterruptedException { stderrReader.join(); return stderr.toString(Charset.defaultCharset()); }
            public void requestStop() { process.destroy(); }
            public void forceStop() { process.destroyForcibly(); }
        };
    }

    private static void transfer(java.io.InputStream input, ByteArrayOutputStream output) {
        try (input) { input.transferTo(output); } catch (IOException ignored) { }
    }
}
