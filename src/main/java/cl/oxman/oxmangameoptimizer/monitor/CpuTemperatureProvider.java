package cl.oxman.oxmangameoptimizer.monitor;

interface CpuTemperatureProvider extends AutoCloseable {

    CpuTemperatureReading read();

    @Override
    default void close() {
    }
}
