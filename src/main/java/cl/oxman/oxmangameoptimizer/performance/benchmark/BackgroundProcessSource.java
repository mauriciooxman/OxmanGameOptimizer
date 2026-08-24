package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.time.Duration;
import java.util.List;

public interface BackgroundProcessSource {
    List<BackgroundProcessObservation> observe(Duration duration);
}
