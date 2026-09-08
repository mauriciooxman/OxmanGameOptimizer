package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessObservation;
import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PreGameBaselineServiceTest {
    @Test void averagesMultipleCpuSamplesAndUsesConservativeMemoryExtremes() {
        var index = new AtomicInteger();
        var id = new ProcessIdentity(123,100,"onedrive.exe");
        var baseline = new PreGameBaselineService(interval -> {
            assertEquals(java.time.Duration.ofSeconds(1), interval);
            int i = index.incrementAndGet();
            return new PreGameBaselineService.Sample(i*10, 60+i, 1000-i, 100+i,
                    List.of(new BackgroundProcessObservation(id,"test",true,false,i)));
        },DiagnosisThresholds.DEFAULT).collect();
        assertEquals(4, baseline.sampleCount());
        assertEquals(25, baseline.cpuAveragePercent());
        assertEquals(2.5, baseline.processes().getFirst().cpuAveragePercent());
        assertEquals(64, baseline.memoryUsedPercent());
        assertEquals(996, baseline.availableMemoryBytes());
        assertEquals(104, baseline.processCount());
    }
    @Test void processChangingIdentityIsNotAveragedIntoACandidate() {
        var i = new AtomicInteger();
        var b = new PreGameBaselineService(interval -> new PreGameBaselineService.Sample(10,50,1000,100,
                List.of(new BackgroundProcessObservation(new ProcessIdentity(123,i.incrementAndGet(),"onedrive.exe"),"test",true,false,10))),DiagnosisThresholds.DEFAULT).collect();
        assertTrue(b.processes().isEmpty());
    }
    @Test void aForegroundSampleExcludesTheProcessForTheWholeWindow() {
        var i = new AtomicInteger();
        var b = new PreGameBaselineService(interval -> new PreGameBaselineService.Sample(10,50,1000,100,
                List.of(new BackgroundProcessObservation(new ProcessIdentity(123,100,"onedrive.exe"),"test",true,i.incrementAndGet()==2,10))),DiagnosisThresholds.DEFAULT).collect();
        assertTrue(b.processes().getFirst().foreground());
    }
    @Test void invalidOrFailedSamplesReturnUnknownInsteadOfZeroLoad() {
        var invalid = new PreGameBaselineService(interval -> new PreGameBaselineService.Sample(Double.NaN,50,100,1,List.of()),DiagnosisThresholds.DEFAULT).collect();
        assertFalse(invalid.complete());
        assertTrue(Double.isNaN(invalid.cpuAveragePercent()));
        assertFalse(new PreGameBaselineService(interval -> { throw new IllegalStateException(); },DiagnosisThresholds.DEFAULT).collect().complete());
    }
    @Test void thresholdsRejectUnsafeAndNonFiniteConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosisThresholds(1,java.time.Duration.ofSeconds(1),75,85,10,3));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosisThresholds(4,java.time.Duration.ofSeconds(1),75,85,10,.5));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosisThresholds(4,java.time.Duration.ofSeconds(1),75,85,Double.NaN,3));
    }
}
