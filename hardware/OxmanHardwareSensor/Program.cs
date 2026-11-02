using System.Text.Json;
using OxmanHardwareSensor;

var jsonOptions = new JsonSerializerOptions
{
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
};

try
{
    using var reader = new LibreHardwareMonitorCpuReader();
    reader.Open();

    string? command;
    while ((command = Console.ReadLine()) is not null)
    {
        string normalizedCommand = command.Trim().TrimStart('\uFEFF');
        if (normalizedCommand.Equals("exit", StringComparison.OrdinalIgnoreCase))
            break;

        CpuTemperatureResult result;
        if (!normalizedCommand.Equals("read", StringComparison.OrdinalIgnoreCase))
        {
            result = CpuTemperatureResult.Unavailable("UNKNOWN_COMMAND");
        }
        else
        {
            try
            {
                result = reader.Read();
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine($"LibreHardwareMonitor read failed: {exception.GetType().Name}: {exception.Message}");
                result = CpuTemperatureResult.Unavailable("READ_FAILED");
            }
        }

        Console.WriteLine(JsonSerializer.Serialize(result, jsonOptions));
        Console.Out.Flush();
    }
}
catch (Exception exception)
{
    Console.Error.WriteLine($"LibreHardwareMonitor initialization failed: {exception.GetType().Name}: {exception.Message}");
    Console.WriteLine(JsonSerializer.Serialize(
        CpuTemperatureResult.Unavailable("INITIALIZATION_FAILED"), jsonOptions));
    Console.Out.Flush();
}
