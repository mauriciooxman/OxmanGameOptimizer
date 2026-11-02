using Xunit;

namespace OxmanHardwareSensor.Tests;

public sealed class CpuSensorSelectorTests
{
    [Fact]
    public void IntelPrefersCpuPackage()
    {
        SelectedCpuSensor? selected = CpuSensorSelector.Select([
            Candidate("Cpu", "Intel Core i7-11800H", "Temperature", "Core Max", 62),
            Candidate("Cpu", "Intel Core i7-11800H", "Temperature", "CPU Package", 58)
        ]);

        Assert.NotNull(selected);
        Assert.Equal("CPU Package", selected.SensorName);
        Assert.Equal(58, selected.TemperatureCelsius);
    }

    [Fact]
    public void IntelFallsBackToCoreMaxThenAverage()
    {
        SelectedCpuSensor? selected = CpuSensorSelector.Select([
            Candidate("Cpu", "Intel Core i7-11800H", "Temperature", "Core Average", 51),
            Candidate("Cpu", "Intel Core i7-11800H", "Temperature", "Core Max", 59)
        ]);

        Assert.NotNull(selected);
        Assert.Equal("Core Max", selected.SensorName);
    }

    [Fact]
    public void AmdPrefersTctlTdie()
    {
        SelectedCpuSensor? selected = CpuSensorSelector.Select([
            Candidate("Cpu", "AMD Ryzen 7 5700X", "Temperature", "CPU Package", 54),
            Candidate("Cpu", "AMD Ryzen 7 5700X", "Temperature", "Core (Tctl)", 57),
            Candidate("Cpu", "AMD Ryzen 7 5700X", "Temperature", "Core (Tctl/Tdie)", 55)
        ]);

        Assert.NotNull(selected);
        Assert.Equal("Core (Tctl/Tdie)", selected.SensorName);
        Assert.Equal(55, selected.TemperatureCelsius);
    }

    [Fact]
    public void IgnoresGpuTemperature()
    {
        SelectedCpuSensor? selected = CpuSensorSelector.Select([
            Candidate("GpuNvidia", "NVIDIA GPU", "Temperature", "CPU Package", 65)
        ]);

        Assert.Null(selected);
    }

    [Fact]
    public void IgnoresMotherboardTemperature()
    {
        SelectedCpuSensor? selected = CpuSensorSelector.Select([
            Candidate("Motherboard", "Mainboard", "Temperature", "CPU Package", 44)
        ]);

        Assert.Null(selected);
    }

    [Fact]
    public void ReturnsUnavailableWithoutReliableNamedSensor()
    {
        SelectedCpuSensor? selected = CpuSensorSelector.Select([
            Candidate("Cpu", "AMD Ryzen 7 5700X", "Temperature", "CCD #1", 52),
            Candidate("Cpu", "AMD Ryzen 7 5700X", "Temperature", "Core (Tctl/Tdie)", 0),
            Candidate("Cpu", "AMD Ryzen 7 5700X", "Load", "CPU Total", 38)
        ]);

        Assert.Null(selected);
    }

    private static SensorCandidate Candidate(
        string hardwareType, string hardwareName, string sensorType, string sensorName, double value) =>
        new(hardwareType, hardwareName, sensorType, sensorName, value);
}
