namespace OxmanHardwareSensor;

internal sealed record CpuTemperatureResult(
    bool Available,
    double? TemperatureCelsius,
    string? Sensor,
    string? Hardware,
    string Provider,
    string? Reason)
{
    internal static CpuTemperatureResult From(SelectedCpuSensor sensor) =>
        new(true, sensor.TemperatureCelsius, sensor.SensorName, sensor.HardwareName,
            "LibreHardwareMonitor", null);

    internal static CpuTemperatureResult Unavailable(string reason) =>
        new(false, null, null, null, "LibreHardwareMonitor", reason);
}
