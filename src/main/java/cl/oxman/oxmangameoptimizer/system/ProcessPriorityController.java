package cl.oxman.oxmangameoptimizer.system;

public interface ProcessPriorityController {
    ProcessIdentity identify(long processId) throws ProcessPriorityException;
    ProcessPriority read(ProcessIdentity process) throws ProcessPriorityException;
    void set(ProcessIdentity process, ProcessPriority priority) throws ProcessPriorityException;
    boolean isSameProcess(ProcessIdentity process);
}
