package processors;

import launchers.PanktiLauncher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import runner.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CandidateTaggerTest {
    static PanktiMain panktiMain;
    static PanktiLauncher panktiLauncher;
    static MavenLauncher mavenLauncher;
    static CtModel testModel;
    static MethodProcessor methodProcessor;
    static CandidateTagger candidateTagger;

    @BeforeAll
    public static void setUpLauncherAndModel() throws URISyntaxException {
        methodProcessor = new MethodProcessor();
        panktiMain = new PanktiMain(Path.of("src/test/resources/jitsi-videobridge"), false);
        panktiLauncher = new PanktiLauncher();
        mavenLauncher = panktiLauncher.getMavenLauncher(panktiMain.getProjectPath().toString(),
                panktiMain.getProjectPath().getFileName().toString());
        testModel = panktiLauncher.buildSpoonModel(mavenLauncher);
        testModel.processWith(methodProcessor);
        candidateTagger = new CandidateTagger();
        candidateTagger.generateReport(methodProcessor.getCandidateMethods());
    }

    @Test
    public void testMethodsRetuningAValue() {
        assertEquals(105, candidateTagger.methodsReturningAValue.size(),
                "105 pure methods in test resource should return a value");
    }

    @Test
    public void testMethodsNotReturningAValue() {
        assertTrue(candidateTagger.methodsNotReturningAValue.isEmpty(),
                "all pure methods in test resource return a value");
    }

    @Test
    public void testMethodsReturningPrimitives() {
        assertEquals(48,
                candidateTagger.methodsReturningAPrimitive.size(),
                "48 pure methods in test resource return a primitive value");
    }

    @Test
    public void testMethodsNotReturningPrimitives() {
        assertEquals(57,
                candidateTagger.methodsReturningAValue.size() -
                        candidateTagger.methodsReturningAPrimitive.size(),
                "57 pure methods in test resource return an object");
    }

    @Test
    public void testMethodsWithIfConditions() {
        assertEquals(4,
                candidateTagger.methodsWithIfConditions.size(),
                "4 pure methods in test resource have an if condition");
    }

    @Test
    public void testMethodsWithConditionals() {
        assertEquals(4,
                candidateTagger.methodsWithConditionalOperators.size(),
                "4 pure methods in test resource use a conditional operator");
    }

    @Test
    public void testMethodsWithLoops() {
        assertTrue(candidateTagger.methodsWithLoops.isEmpty(),
                "No pure method in test resource has a loop");
    }

    @Test
    public void testMethodsWithSwitchCases() {
        assertEquals(1,
                candidateTagger.methodWithSwitchCases.size(),
                "1 pure method in test resource has switch cases");
    }

    @Test
    public void testMethodsWithParameters() {
        assertEquals(16,
                candidateTagger.methodsWithParameters.size(),
                "16 pure methods in test resource have parameters");
    }

    @Test
    public void testMethodsWithMultipleStatements() {
        assertEquals(4,
                candidateTagger.methodsWithMultipleStatements.size(),
                "4 pure methods in test resource have multiple statements");
    }

    @Test
    public void testMethodsWithLocalVariables() {
        assertEquals(2,
                candidateTagger.methodsWithLocalVariables.size(),
                "2 pure methods in test resource define local variables");
    }
}
