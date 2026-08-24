package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public record BenchmarkTarget(String gameName, Supplier<Optional<String>> processNameLookup,
                              BooleanSupplier running) {
    public Optional<String> findProcessName() { return processNameLookup.get(); }

    @Override public String toString() { return gameName; }
}
