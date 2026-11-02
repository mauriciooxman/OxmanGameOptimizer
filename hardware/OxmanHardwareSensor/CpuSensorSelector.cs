namespace OxmanHardwareSensor;

internal static class CpuSensorSelector
{
    private const double MaximumReasonableCelsius = 125.0;

    internal static SelectedCpuSensor? Select(IEnumerable<SensorCandidate> source)
    {
        return source
            .Where(IsValidCpuTemperature)
            .Select(candidate => new { Candidate = candidate, Rank = Rank(candidate) })
            .Where(value => value.Rank < int.MaxValue)
            .OrderBy(value => value.Rank)
            .ThenBy(value => value.Candidate.SensorName, StringComparer.OrdinalIgnoreCase)
            .Select(value => new SelectedCpuSensor(
                value.Candidate.HardwareName,
                value.Candidate.SensorName,
                value.Candidate.Value!.Value))
            .FirstOrDefault();
    }

    private static bool IsValidCpuTemperature(SensorCandidate value) =>
        value.HardwareType.Equals("Cpu", StringComparison.OrdinalIgnoreCase)
        && value.SensorType.Equals("Temperature", StringComparison.OrdinalIgnoreCase)
        && value.Value is double temperature
        && double.IsFinite(temperature)
        && temperature > 0.0
        && temperature <= MaximumReasonableCelsius;

    private static int Rank(SensorCandidate value)
    {
        bool amd = value.HardwareName.Contains("AMD", StringComparison.OrdinalIgnoreCase)
                   || value.HardwareName.Contains("Ryzen", StringComparison.OrdinalIgnoreCase);
        string name = value.SensorName.Trim();

        if (amd)
        {
            if (name.Equals("Core (Tctl/Tdie)", StringComparison.OrdinalIgnoreCase)) return 0;
            if (name.Equals("Core (Tdie)", StringComparison.OrdinalIgnoreCase)) return 1;
            if (name.Equals("Core (Tctl)", StringComparison.OrdinalIgnoreCase)) return 2;
            if (name.Equals("CPU Package", StringComparison.OrdinalIgnoreCase)) return 3;
            if (name.Equals("Core Max", StringComparison.OrdinalIgnoreCase)) return 4;
            if (name.Equals("Core Average", StringComparison.OrdinalIgnoreCase)) return 5;
            return int.MaxValue;
        }

        if (name.Equals("CPU Package", StringComparison.OrdinalIgnoreCase)) return 0;
        if (name.Equals("Core Max", StringComparison.OrdinalIgnoreCase)) return 1;
        if (name.Equals("Core Average", StringComparison.OrdinalIgnoreCase)) return 2;
        if (name.Equals("Core (Tctl/Tdie)", StringComparison.OrdinalIgnoreCase)) return 3;
        if (name.Equals("Core (Tdie)", StringComparison.OrdinalIgnoreCase)) return 4;
        if (name.Equals("Core (Tctl)", StringComparison.OrdinalIgnoreCase)) return 5;
        return int.MaxValue;
    }
}
