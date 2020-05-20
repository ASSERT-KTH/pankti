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
    public void testProtectedMethods() {
        assertEquals(79,
                methodProcessor.protectedMethods.size(),
                "Number of protected methods in test resource is 79");
    }

    @Test
    public void testAbstractMethods() {
        assertEquals(41,
                methodProcessor.abstractMethods.size(),
                "Number of abstract methods in test resource is 41");
    }

    @Test
    public void testMethodsThrowingExceptions() {
        assertEquals(95,
                methodProcessor.methodsThrowingExceptions.size(),
                "Number of methods in test resource that throw exceptions is 95");
    }

    @Test
    public void testMethodsModifyingFields() {
        assertEquals(19,
                methodProcessor.methodsWithFieldAssignments.size(),
                "Number of methods in test resource that modify fields is 19");
    }

    @Test
    public void testMethodsInvokingConstructors() {
        assertEquals(3,
                methodProcessor.methodsWithConstructorCalls.size(),
                "Number of methods in test resource that invoke constructors is 3");
    }

    @Test
    public void testMethodsInvokingOtherMethods() {
        assertEquals(650,
                methodProcessor.methodsWithInvocations.size(),
                "Number of methods in test resource that invoke other methods is 650");
    }

    @Test
    public void testSynchronizedMethods() {
        assertEquals(15,
                methodProcessor.methodsWithSynchronization.size(),
                "Number of methods in test resource with synchronization is 15");
    }

    @Test
    public void testDeprecatedMethods() {
        assertEquals(0,
                methodProcessor.deprecatedMethods.size(),
                "Number of deprecated methods in test resource is 0");
    }

    @Test
    public void testEmptyMethods() {
        assertEquals(19,
                methodProcessor.emptyMethods.size(),
                "Number of empty methods in test resource is 19");
    }

    @Test
    public void testMethodsModifyingArrayArguments() {
        assertEquals(0,
                methodProcessor.methodsModifyingArrayArguments.size(),
                "Number of methods in test resource that modify array arguments is 0");
    }

    @Test
    public void testMethodsModifyingNonLocalVariables() {
        assertEquals(0,
                methodProcessor.methodsModifyingNonLocalVariables.size(),
                "Number of methods in test resource that modify non-local variables is 188");
    }

    @Test
    public void testAnnotationTypeMethods() {
        assertEquals(2,
                methodProcessor.methodsInAnnotationType.size(),
                "Number of methods in annotation types in test resource is 2");
    }

    @Test
    public void testCandidateMethods() {
        assertEquals(105,
                methodProcessor.candidateMethods.size(),
                "Number of pure methods in test resource 105");
    }
}
