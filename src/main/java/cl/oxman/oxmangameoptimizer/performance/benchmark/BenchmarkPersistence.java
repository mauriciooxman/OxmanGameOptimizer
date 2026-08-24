package cl.oxman.oxmangameoptimizer.performance.benchmark;

@FunctionalInterface
public interface BenchmarkPersistence { void save(BenchmarkRecord record) throws Exception; }
