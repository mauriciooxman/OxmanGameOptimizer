package cl.oxman.oxmangameoptimizer.system;

public enum ProcessPriority {
    IDLE(0x00000040), BELOW_NORMAL(0x00004000), NORMAL(0x00000020),
    ABOVE_NORMAL(0x00008000), HIGH(0x00000080), REALTIME(0x00000100);

    private final int windowsValue;
    ProcessPriority(int windowsValue) { this.windowsValue = windowsValue; }
    public int windowsValue() { return windowsValue; }

    public static ProcessPriority fromWindowsValue(int value) {
        for (ProcessPriority priority : values()) if (priority.windowsValue == value) return priority;
        throw new IllegalArgumentException("Unsupported Windows priority class: 0x" + Integer.toHexString(value));
    }
}
