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

public class MethodProcessorTest {
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
    public void testPomPath() {
        assertEquals(panktiMain.getProjectPath().toString() + "/pom.xml",
                mavenLauncher.getPomFile().getPath(),
                "POM file must be present in project path");
    }

    @Test
    public void testMethodCount() {
        assertEquals(893,
                panktiLauncher.countMethods(testModel),
                "Total number of methods in test resource is 893");
    }

    @Test
    public void testPublicMethods() {
        assertEquals(574,
                methodProcessor.publicMethods.size(),
                "Number of public methods in test resource is 574");
    }

    @Test
    public void testPrivateMethods() {
        assertEquals(188,
                methodProcessor.privateMethods.size(),
                "Number of private methods in test resource is 188");
    }

    @Test
    public void testSynchronizedMethods() {
        assertEquals(15,
                methodProcessor.methodsWithSynchronization.size(),
                "Number of synchronized methods, synchronized blocks in test resource is 188");
    }

    @Test
    public void testDeprecatedMethods() {
        assertEquals(0,
                methodProcessor.deprecatedMethods.size(),
                "Number of deprecated methods in test resource is 188");
    }

    @Test
    public void testEmptyMethods() {
        assertEquals(19,
                methodProcessor.emptyMethods.size(),
                "Number of empty methods in test resource is 188");
    }

    @Test
    public void testAnnotationTypeMethods() {
        assertEquals(2,
                methodProcessor.methodsInAnnotationType.size(),
                "Number of methods in annotation types in test resource is 188");
    }

    @Test
    public void testCandidateMethods() {
        assertEquals(105,
                methodProcessor.candidateMethods.size(),
                "Number of candidate methods in test resource 105");
    }

    @Test
    public void testMethodsReturningPrimitives() {
        assertEquals(48,
                candidateTagger.methodsReturningAPrimitive.size(),
                "48 pure methods return a primitive value");
    }
}
