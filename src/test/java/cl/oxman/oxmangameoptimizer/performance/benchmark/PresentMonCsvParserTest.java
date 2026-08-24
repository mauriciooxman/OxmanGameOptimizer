package cl.oxman.oxmangameoptimizer.performance.benchmark;

import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class PresentMonCsvParserTest {
    private final PresentMonCsvParser parser = new PresentMonCsvParser();
    @Test void parsesQuotedCsvAndExtraColumns() throws Exception {
        var result = parser.parse(new StringReader("Application,FrameTime,msCPUBusy,msGPUActive,Extra\n\"game, x\",10,4,7,x\napp,20,5,8,y\n"), "game.exe", Duration.ofSeconds(1));
        assertEquals(2, result.frameCount()); assertEquals(1000d / 15d, result.averageFps().orElseThrow(), 0.01);
        assertEquals(50, result.onePercentLow().orElseThrow(), 0.01);
    }
    @Test void missingColumnsProduceEmptyOptionals() throws Exception {
        var result = parser.parse(new StringReader("Application,Other\ngame,x\n"), "game.exe", Duration.ZERO);
        assertTrue(result.averageFps().isEmpty()); assertTrue(result.gpuFrameTimeMs().isEmpty());
    }
    @Test void corruptAndEmptyValuesAreIgnored() throws Exception {
        var result = parser.parse(new StringReader("FrameTime\nnope\n\n10\n"), "game.exe", Duration.ZERO);
        assertEquals(100, result.averageFps().orElseThrow(), 0.01);
    }
}
