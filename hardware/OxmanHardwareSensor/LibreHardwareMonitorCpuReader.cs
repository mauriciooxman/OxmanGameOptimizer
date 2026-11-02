using LibreHardwareMonitor.Hardware;

namespace OxmanHardwareSensor;

internal sealed class LibreHardwareMonitorCpuReader : IDisposable
{
    private readonly Computer computer = new()
    {
        IsCpuEnabled = true
    };
    private bool opened;

    internal void Open()
    {
        computer.Open();
        opened = true;
    }

    internal CpuTemperatureResult Read()
    {
        var candidates = new List<SensorCandidate>();
        foreach (IHardware hardware in computer.Hardware.Where(
                     value => value.HardwareType == HardwareType.Cpu))
        {
            UpdateAndCollect(hardware, hardware.Name, candidates);
        }

        SelectedCpuSensor? selected = CpuSensorSelector.Select(candidates);
        if (selected is not null) return CpuTemperatureResult.From(selected);

        string reason = OperatingSystem.IsWindows()
                        && !LibreHardwareMonitor.PawnIo.PawnIo.IsInstalled
            ? "PAWNIO_NOT_INSTALLED"
            : "NO_RELIABLE_CPU_SENSOR";
        return CpuTemperatureResult.Unavailable(reason);
    }

    private static void UpdateAndCollect(
        IHardware hardware, string cpuHardwareName, ICollection<SensorCandidate> candidates)
    {
        hardware.Update();
        foreach (ISensor sensor in hardware.Sensors)
        {
            candidates.Add(new SensorCandidate(
                "Cpu",
                cpuHardwareName,
                sensor.SensorType.ToString(),
                sensor.Name,
                sensor.Value));
        }

        foreach (IHardware subHardware in hardware.SubHardware)
        {
            UpdateAndCollect(subHardware, cpuHardwareName, candidates);
        }
    }

    public void Dispose()
    {
        if (!opened) return;
        computer.Close();
        opened = false;
    }
}
