package cl.oxman.oxmangameoptimizer.system;

/** Exact Win32 PROCESS_POWER_THROTTLING_STATE value. */
public record ProcessPowerThrottlingState(int version, int controlMask, int stateMask) {
    public boolean isExecutionSpeedDisabled() {
        return (controlMask & ProcessPowerThrottlingService.EXECUTION_SPEED) != 0
                && (stateMask & ProcessPowerThrottlingService.EXECUTION_SPEED) == 0;
    }

    public boolean isExecutionSpeedEnabled() {
        return (controlMask & ProcessPowerThrottlingService.EXECUTION_SPEED) != 0
                && (stateMask & ProcessPowerThrottlingService.EXECUTION_SPEED) != 0;
    }

    public ProcessPowerThrottlingState withExecutionSpeedEnabled() {
        return new ProcessPowerThrottlingState(version,
                controlMask | ProcessPowerThrottlingService.EXECUTION_SPEED,
                stateMask | ProcessPowerThrottlingService.EXECUTION_SPEED);
    }

    public String nativeDisplay() {
        return "Version=" + version + " ControlMask=0x" + Integer.toHexString(controlMask).toUpperCase()
                + " StateMask=0x" + Integer.toHexString(stateMask).toUpperCase();
    }
}
