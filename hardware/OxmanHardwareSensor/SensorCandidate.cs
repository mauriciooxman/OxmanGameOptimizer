namespace OxmanHardwareSensor;

internal sealed record SensorCandidate(
    string HardwareType,
    string HardwareName,
    string SensorType,
    string SensorName,
    double? Value);

internal sealed record SelectedCpuSensor(
    string HardwareName,
    string SensorName,
    double TemperatureCelsius);
