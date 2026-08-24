package cl.oxman.oxmangameoptimizer.system;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.FILETIME;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.win32.StdCallLibrary;

import java.nio.file.Path;
import java.util.List;

/** Win32 Get/SetProcessInformation implementation with PID-reuse protection. */
public final class WindowsProcessPowerThrottlingService implements ProcessPowerThrottlingService {
    static final int PROCESS_POWER_THROTTLING = 4;
    private static final int QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int SET_INFORMATION = 0x0200;
    private static final int ERROR_ACCESS_DENIED = 5;
    private final NativeApi api;

    public WindowsProcessPowerThrottlingService() { this(NativeApi.INSTANCE); }
    WindowsProcessPowerThrottlingService(NativeApi api) { this.api = api; }

    interface NativeApi extends StdCallLibrary {
        NativeApi INSTANCE = Native.load("kernel32", NativeApi.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean GetProcessInformation(HANDLE process, int informationClass, PowerState state, int size);
        boolean SetProcessInformation(HANDLE process, int informationClass, PowerState state, int size);
    }

    @Structure.FieldOrder({"Version", "ControlMask", "StateMask"})
    public static class PowerState extends Structure {
        public int Version;
        public int ControlMask;
        public int StateMask;
        public PowerState() { }
        PowerState(ProcessPowerThrottlingState state) {
            Version = state.version(); ControlMask = state.controlMask(); StateMask = state.stateMask(); write();
        }
    }

    @Override public ProcessIdentity identify(long pid) {
        if (pid <= 0 || pid > 0xffff_ffffL) throw failure(ProcessPowerThrottlingException.Reason.INVALID_PID, "PID inválido", 0);
        HANDLE handle = open(pid, QUERY_LIMITED_INFORMATION);
        try {
            FILETIME creation = new FILETIME(), exit = new FILETIME(), kernel = new FILETIME(), user = new FILETIME();
            if (!Kernel32.INSTANCE.GetProcessTimes(handle, creation, exit, kernel, user)) throw nativeFailure("GetProcessTimes");
            String name = ProcessHandle.of(pid).flatMap(p -> p.info().command()).map(Path::of)
                    .map(Path::getFileName).map(Path::toString).orElse("pid-" + pid);
            return new ProcessIdentity(pid, fileTimeToEpochMillis(creation), name);
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }

    @Override public ProcessPowerThrottlingState read(ProcessIdentity process) {
        HANDLE handle = verifiedOpen(process, QUERY_LIMITED_INFORMATION);
        try {
            PowerState state = new PowerState();
            state.Version = CURRENT_VERSION;
            state.write();
            if (!api.GetProcessInformation(handle, PROCESS_POWER_THROTTLING, state, state.size()))
                throw nativeFailure("GetProcessInformation(ProcessPowerThrottling)");
            state.read();
            return new ProcessPowerThrottlingState(state.Version, state.ControlMask, state.StateMask);
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }

    @Override public void applyHighQos(ProcessIdentity process) {
        write(process, new ProcessPowerThrottlingState(CURRENT_VERSION, EXECUTION_SPEED, 0));
    }

    @Override public void applyEcoQos(ProcessIdentity process, ProcessPowerThrottlingState current) {
        write(process, current.withExecutionSpeedEnabled());
    }

    @Override public void restore(ProcessIdentity process, ProcessPowerThrottlingState state) { write(process, state); }

    private void write(ProcessIdentity process, ProcessPowerThrottlingState value) {
        HANDLE handle = verifiedOpen(process, QUERY_LIMITED_INFORMATION | SET_INFORMATION);
        try {
            PowerState state = new PowerState(value);
            if (!api.SetProcessInformation(handle, PROCESS_POWER_THROTTLING, state, state.size()))
                throw nativeFailure("SetProcessInformation(ProcessPowerThrottling)");
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }

    @Override public boolean isSameProcess(ProcessIdentity process) {
        try { return identify(process.pid()).startEpochMillis() == process.startEpochMillis(); }
        catch (ProcessPowerThrottlingException exception) { return false; }
    }

    private HANDLE verifiedOpen(ProcessIdentity process, int access) {
        ProcessIdentity current = identify(process.pid());
        if (current.startEpochMillis() != process.startEpochMillis())
            throw failure(ProcessPowerThrottlingException.Reason.PID_REUSED, "El PID pertenece a otro proceso", 0);
        return open(process.pid(), access);
    }
    private static HANDLE open(long pid, int access) {
        HANDLE handle = Kernel32.INSTANCE.OpenProcess(access, false, (int) pid);
        if (handle == null) {
            int error = Native.getLastError();
            var reason = error == ERROR_ACCESS_DENIED ? ProcessPowerThrottlingException.Reason.ACCESS_DENIED
                    : ProcessHandle.of(pid).isEmpty() ? ProcessPowerThrottlingException.Reason.PROCESS_ENDED
                    : ProcessPowerThrottlingException.Reason.NATIVE_FAILURE;
            throw failure(reason, "OpenProcess falló", error);
        }
        return handle;
    }
    private static long fileTimeToEpochMillis(FILETIME time) {
        long ticks = (Integer.toUnsignedLong(time.dwHighDateTime) << 32) | Integer.toUnsignedLong(time.dwLowDateTime);
        return ticks / 10_000L - 11_644_473_600_000L;
    }
    private static ProcessPowerThrottlingException nativeFailure(String operation) {
        int error = Native.getLastError();
        return failure(error == ERROR_ACCESS_DENIED ? ProcessPowerThrottlingException.Reason.ACCESS_DENIED
                : ProcessPowerThrottlingException.Reason.NATIVE_FAILURE, operation + " falló", error);
    }
    private static ProcessPowerThrottlingException failure(ProcessPowerThrottlingException.Reason reason, String text, int code) {
        return new ProcessPowerThrottlingException(reason, text + (code == 0 ? "" : " (Win32 " + code + ")"));
    }
}
