package cl.oxman.oxmangameoptimizer.system;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.FILETIME;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;

import java.nio.file.Path;

/** Win32 implementation. Every operation verifies PID + creation time before changing state. */
public final class WindowsProcessPriorityService implements ProcessPriorityController {
    private static final int QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int SET_INFORMATION = 0x0200;
    private static final int ERROR_ACCESS_DENIED = 5;

    @Override public ProcessIdentity identify(long pid) {
        if (pid <= 0 || pid > 0xffff_ffffL) throw failure(ProcessPriorityException.Reason.INVALID_PID, "PID inválido", 0);
        HANDLE handle = open(pid, QUERY_LIMITED_INFORMATION);
        try {
            FILETIME creation = new FILETIME(), exit = new FILETIME(), kernel = new FILETIME(), user = new FILETIME();
            if (!Kernel32.INSTANCE.GetProcessTimes(handle, creation, exit, kernel, user)) throw nativeFailure("GetProcessTimes");
            String name = ProcessHandle.of(pid).flatMap(p -> p.info().command()).map(Path::of)
                    .map(Path::getFileName).map(Path::toString).orElse("pid-" + pid);
            return new ProcessIdentity(pid, fileTimeToEpochMillis(creation), name);
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }

    @Override public ProcessPriority read(ProcessIdentity process) {
        HANDLE handle = verifiedOpen(process, QUERY_LIMITED_INFORMATION);
        try {
            int value = Kernel32.INSTANCE.GetPriorityClass(handle).intValue();
            if (value == 0) throw nativeFailure("GetPriorityClass");
            return ProcessPriority.fromWindowsValue(value);
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }

    @Override public void set(ProcessIdentity process, ProcessPriority priority) {
        HANDLE handle = verifiedOpen(process, QUERY_LIMITED_INFORMATION | SET_INFORMATION);
        try {
            if (!Kernel32.INSTANCE.SetPriorityClass(handle, new DWORD(priority.windowsValue())))
                throw nativeFailure("SetPriorityClass");
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }

    @Override public boolean isSameProcess(ProcessIdentity process) {
        try { return identify(process.pid()).startEpochMillis() == process.startEpochMillis(); }
        catch (ProcessPriorityException exception) { return false; }
    }

    private HANDLE verifiedOpen(ProcessIdentity process, int access) {
        ProcessIdentity current = identify(process.pid());
        if (current.startEpochMillis() != process.startEpochMillis())
            throw failure(ProcessPriorityException.Reason.PID_REUSED, "El PID pertenece a otro proceso", 0);
        return open(process.pid(), access);
    }

    private static HANDLE open(long pid, int access) {
        HANDLE handle = Kernel32.INSTANCE.OpenProcess(access, false, (int) pid);
        if (handle == null) {
            int error = Native.getLastError();
            var reason = error == ERROR_ACCESS_DENIED ? ProcessPriorityException.Reason.ACCESS_DENIED
                    : ProcessHandle.of(pid).isEmpty() ? ProcessPriorityException.Reason.PROCESS_ENDED
                    : ProcessPriorityException.Reason.NATIVE_FAILURE;
            throw failure(reason, "OpenProcess falló", error);
        }
        return handle;
    }

    private static long fileTimeToEpochMillis(FILETIME time) {
        long windowsTicks = (Integer.toUnsignedLong(time.dwHighDateTime) << 32)
                | Integer.toUnsignedLong(time.dwLowDateTime);
        return windowsTicks / 10_000L - 11_644_473_600_000L;
    }
    private static ProcessPriorityException nativeFailure(String operation) {
        int error = Native.getLastError();
        return failure(error == ERROR_ACCESS_DENIED ? ProcessPriorityException.Reason.ACCESS_DENIED
                : ProcessPriorityException.Reason.NATIVE_FAILURE, operation + " falló", error);
    }
    private static ProcessPriorityException failure(ProcessPriorityException.Reason reason, String text, int code) {
        return new ProcessPriorityException(reason, text + (code == 0 ? "" : " (Win32 " + code + ")"));
    }
}
