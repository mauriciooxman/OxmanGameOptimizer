package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.User32;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Short, one-shot CPU observation; it does not mutate or retain process handles. */
public final class WindowsBackgroundProcessSource implements BackgroundProcessSource {
    @Override public List<BackgroundProcessObservation> observe(Duration duration) {
        long foregroundBefore = foregroundPid();
        String currentUser = normalizeUser(System.getProperty("user.name", ""));
        Map<Long, Sample> before = samples();
        long observationStarted = System.nanoTime();
        try { Thread.sleep(Math.max(1, duration.toMillis())); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return List.of(); }
        long elapsedNanos = Math.max(1, System.nanoTime() - observationStarted);
        long foregroundAfter = foregroundPid();
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        List<BackgroundProcessObservation> result = new ArrayList<>();
        for (Sample after : samples().values()) {
            Sample first = before.get(after.identity().pid());
            if (first == null || first.identity().startEpochMillis() != after.identity().startEpochMillis()) continue;
            long cpuDelta = Math.max(0, after.cpuNanos() - first.cpuNanos());
            double cpu = cpuDelta * 100.0 / elapsedNanos / processors;
            String user = after.user();
            result.add(new BackgroundProcessObservation(after.identity(), user,
                    normalizeUser(user).equals(currentUser),
                    after.identity().pid() == foregroundBefore || after.identity().pid() == foregroundAfter, cpu));
        }
        return result;
    }

    private static Map<Long, Sample> samples() {
        Map<Long, Sample> result = new HashMap<>();
        ProcessHandle.allProcesses().forEach(process -> {
            try {
                var info = process.info();
                if (info.command().isEmpty() || info.startInstant().isEmpty() || info.totalCpuDuration().isEmpty()) return;
                String name = Path.of(info.command().get()).getFileName().toString();
                String arguments = String.join(" ", info.arguments().orElse(new String[0])).toLowerCase(Locale.ROOT);
                if (arguments.contains("audio") || arguments.contains("webrtc") || arguments.contains("input")) return;
                String lowerName = name.toLowerCase(Locale.ROOT);
                if ((lowerName.equals("chrome.exe") || lowerName.equals("msedge.exe") || lowerName.equals("firefox.exe"))
                        && arguments.contains("--type=")) return;
                ProcessIdentity identity = new ProcessIdentity(process.pid(), info.startInstant().get().toEpochMilli(), name);
                result.put(process.pid(), new Sample(identity, info.user().orElse(""), info.totalCpuDuration().get().toNanos()));
            } catch (RuntimeException ignored) { }
        });
        return result;
    }
    private static long foregroundPid() {
        try {
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(User32.INSTANCE.GetForegroundWindow(), pid);
            return Integer.toUnsignedLong(pid.getValue());
        } catch (RuntimeException exception) { return -1; }
    }
    private static String normalizeUser(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        int slash = Math.max(normalized.lastIndexOf('\\'), normalized.lastIndexOf('/'));
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }
    private record Sample(ProcessIdentity identity, String user, long cpuNanos) { }
}
