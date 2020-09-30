package se.kth.castor.pankti.extract.processors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.extract.launchers.PanktiLauncher;
import se.kth.castor.pankti.extract.runners.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.TypeFilter;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MethodProcessorTest {
    static PanktiMain panktiMain;
    static PanktiLauncher panktiLauncher;
    static MavenLauncher mavenLauncher;
    static CtModel testModel;
    static MethodProcessor methodProcessor;

    @BeforeAll
    public static void setUpLauncherAndModel() throws URISyntaxException {
        methodProcessor = new MethodProcessor();
        panktiMain = new PanktiMain(Path.of("src/test/resources/jitsi-videobridge"), false);
        panktiLauncher = new PanktiLauncher();
        mavenLauncher = panktiLauncher.getMavenLauncher(panktiMain.getProjectPath().toString(),
                panktiMain.getProjectPath().getFileName().toString());
        testModel = panktiLauncher.buildSpoonModel(mavenLauncher);
        testModel.processWith(methodProcessor);
        panktiLauncher.addMetaDataToCandidateMethods(methodProcessor.getCandidateMethods());
    }

    // Test that the project has a POM file in the root path
    @Test
    public void testPomPath() {
        assertEquals(panktiMain.getProjectPath().toString() + "/pom.xml",
                mavenLauncher.getPomFile().getPath(),
                "POM file must be present in project path");
    }

    // Test the total number of methods in the project
    @Test
    public void testMethodCount() {
        assertEquals(893,
                panktiLauncher.countMethods(testModel),
                "Total number of methods in test resource is 893");
    }

    // Test the number of public methods in the project
    @Test
    public void testNumberOfPublicMethods() {
        assertEquals(574,
                methodProcessor.publicMethods.size(),
                "Number of public methods in test resource is 574");
    }

    // Test that a method classified as public is indeed public
    @Test
    public void testPublicMethod() {
        assertTrue(methodProcessor.publicMethods.get(0).getModifiers().contains(ModifierKind.PUBLIC),
                "Method should have a public modifier");
    }

    // Test the number of private methods in the project
    @Test
    public void testNumberOfPrivateMethods() {
        assertEquals(188,
                methodProcessor.privateMethods.size(),
                "Number of private methods in test resource is 188");
    }

    // Test that a method classified as private is indeed private
    @Test
    public void testPrivateMethod() {
        assertTrue(methodProcessor.privateMethods.get(0).getModifiers().contains(ModifierKind.PRIVATE),
                "Method should have a private modifier");
    }

    // Test the number of protected methods in the project
    @Test
    public void testNumberOfProtectedMethods() {
        assertEquals(79,
                methodProcessor.protectedMethods.size(),
                "Number of protected methods in test resource is 79");
    }

    // Test that a method classified as protected is indeed protected
    @Test
    public void testProtectedMethod() {
        assertTrue(methodProcessor.protectedMethods.get(0).getModifiers().contains(ModifierKind.PROTECTED),
                "Method should have a protected modifier");
    }

    // Test the number of abstract methods in the project
    @Test
    public void testNumberOfAbstractMethods() {
        assertEquals(41,
                methodProcessor.abstractMethods.size(),
                "Number of abstract methods in test resource is 41");
    }

    // Test that a method classified as abstract is indeed abstract
    @Test
    public void testAbstractMethods() {
        CtMethod<?> abstractMethod = methodProcessor.abstractMethods.get(0);
        assertTrue(abstractMethod.isAbstract(),
                "Method should be abstract");
        assertFalse(methodProcessor.candidateMethods.contains(abstractMethod),
                "An abstract method is not extracted");
    }

    // Test the number of methods that are classified as deprecated
    @Test
    public void testNumberOfDeprecatedMethods() {
        assertEquals(0,
                methodProcessor.deprecatedMethods.size(),
                "Number of deprecated methods in test resource is 0");
    }

    // Test that a method classified as deprecated actually is deprecated or has a deprecated parent
    @Test
    public void testDeprecatedMethod() {
        for (CtMethod<?> extractedMethod : methodProcessor.candidateMethods) {
            assertFalse((extractedMethod.hasAnnotation(Deprecated.class) ||
                            extractedMethod.getParent().hasAnnotation(Deprecated.class)),
                    "No method or its parent is deprecated in test resource");
        }
    }

    // Test the number of methods that are empty
    @Test
    public void testNumberOfEmptyMethods() {
        assertEquals(19,
                methodProcessor.emptyMethods.size(),
                "Number of empty methods in test resource is 19");
    }

    // Test that a method classified as empty indeed has no statements, and is not extracted
    @Test
    public void testEmptyMethod() {
        CtMethod<?> emptyMethod = methodProcessor.emptyMethods.get(0);
        assertFalse(emptyMethod.getBody().getStatements().size() > 0,
                "Method should not have any statements");
        assertFalse(methodProcessor.candidateMethods.contains(emptyMethod),
                "An empty method is not extracted");
    }

    // Test the number of methods whose parents are annotation types
    @Test
    public void testNumberOfAnnotationTypeMethods() {
        assertEquals(2,
                methodProcessor.methodsInAnnotationType.size(),
                "Number of methods in annotation types in test resource is 2");
    }

    // Test the total number of extracted methods found in the test resource
    @Test
    public void testNumberOfCandidateMethods() {
        assertEquals(214,
                methodProcessor.candidateMethods.size(),
                "Number of extracted methods in test resource 214");
    }
}
