package cl.oxman.oxmangameoptimizer.system;

/** Testable boundary for Windows process power-throttling policy. */
public interface ProcessPowerThrottlingService {
    // processthreadsapi.h: PROCESS_POWER_THROTTLING_CURRENT_VERSION and EXECUTION_SPEED.
    int CURRENT_VERSION = 1;
    int EXECUTION_SPEED = 0x1;

    ProcessIdentity identify(long pid);
    ProcessPowerThrottlingState read(ProcessIdentity process);
    void applyHighQos(ProcessIdentity process);
    void applyEcoQos(ProcessIdentity process, ProcessPowerThrottlingState current);
    void restore(ProcessIdentity process, ProcessPowerThrottlingState state);
    boolean isSameProcess(ProcessIdentity process);

    default boolean verifyHighQos(ProcessIdentity process) {
        return read(process).isExecutionSpeedDisabled();
    }
    default boolean verifyEcoQos(ProcessIdentity process) {
        return read(process).isExecutionSpeedEnabled();
    }
}
