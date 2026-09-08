package cl.oxman.oxmangameoptimizer.optimizer.assessment;

/** Custom/OEM plans are unknown, not automatically assumed inferior. */
public final class PowerPlanPolicy {
    public static final String HIGH = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";
    public static boolean optimized(String guid) {
        return HIGH.equalsIgnoreCase(guid) || "e9a42b02-d5df-448d-aa00-03f14749eb61".equalsIgnoreCase(guid);
    }
    public static boolean changeable(String guid) {
        return "381b4222-f694-41f0-9685-ff5bb260df2e".equalsIgnoreCase(guid)
                || "a1841308-3541-4fab-bc81-f71556f20b4a".equalsIgnoreCase(guid);
    }
    private PowerPlanPolicy() { }
}
