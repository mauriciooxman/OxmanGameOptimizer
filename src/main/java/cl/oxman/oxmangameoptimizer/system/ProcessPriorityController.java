package cl.oxman.oxmangameoptimizer.system;

public interface ProcessPriorityController {
    ProcessIdentity identify(long processId) throws ProcessPriorityException;
    ProcessPriority read(ProcessIdentity process) throws ProcessPriorityException;
    default ProcessPriorityReading readWithNativeValue(ProcessIdentity process) throws ProcessPriorityException {
        ProcessPriority priority = read(process);
        return new ProcessPriorityReading(priority, priority.windowsValue());
    }
    void set(ProcessIdentity process, ProcessPriority priority) throws ProcessPriorityException;
    boolean isSameProcess(ProcessIdentity process);
}
