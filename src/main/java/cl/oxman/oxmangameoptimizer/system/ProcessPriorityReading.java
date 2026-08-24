package cl.oxman.oxmangameoptimizer.system;

/** One GetPriorityClass result, retaining the exact DWORD returned by Win32. */
public record ProcessPriorityReading(ProcessPriority priority, int windowsValue) {
    public ProcessPriorityReading {
        if (ProcessPriority.fromWindowsValue(windowsValue) != priority)
            throw new IllegalArgumentException("Priority and native value do not match");
    }

    public String display() { return priority.displayWithWindowsValue(); }
}
