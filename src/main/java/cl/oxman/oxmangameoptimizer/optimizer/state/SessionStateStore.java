package cl.oxman.oxmangameoptimizer.optimizer.state;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Crash-safe, non-personal recovery state stored under LOCALAPPDATA. */
public final class SessionStateStore {
    private final Path file;

    public SessionStateStore(Path file) { this.file = file; }

    public static SessionStateStore localAppData() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return new SessionStateStore(base.resolve("OxmanGameOptimizer").resolve("session-state.json"));
    }

    public synchronized void save(SessionState state) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        StringBuilder json = new StringBuilder("{\n  \"active\": true,\n  \"game\": \"")
                .append(encode(state.gameName())).append("\",\n  \"startedAt\": ")
                .append(state.startedAt()).append(",\n  \"changes\": [\n");
        for (int i = 0; i < state.changes().size(); i++) {
            AppliedChange change = state.changes().get(i);
            json.append("    {\"id\": \"").append(encode(change.actionId()))
                    .append("\", \"original\": \"").append(encode(change.originalState())).append("\"}");
            if (i + 1 < state.changes().size()) json.append(',');
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized Optional<SessionState> load() throws IOException {
        if (!Files.isRegularFile(file)) return Optional.empty();
        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (!json.contains("\"active\": true")) return Optional.empty();
        String game = decode(field(json, "game"));
        long started = Long.parseLong(numberField(json, "startedAt"));
        List<AppliedChange> changes = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile(
                "\\{\\\"id\\\": \\\"([^\\\"]*)\\\", \\\"original\\\": \\\"([^\\\"]*)\\\"}").matcher(json);
        while (matcher.find()) changes.add(new AppliedChange(decode(matcher.group(1)), decode(matcher.group(2))));
        return Optional.of(new SessionState(game, started, changes));
    }

    public synchronized void complete() throws IOException { Files.deleteIfExists(file); }
    public Path path() { return file; }

    private static String field(String json, String name) throws IOException {
        var matcher = java.util.regex.Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        if (!matcher.find()) throw new IOException("Invalid recovery state: " + name);
        return matcher.group(1);
    }

    private static String numberField(String json, String name) throws IOException {
        var matcher = java.util.regex.Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new IOException("Invalid recovery state: " + name);
        return matcher.group(1);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) throws IOException {
        try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid recovery state encoding", exception); }
    }
}
