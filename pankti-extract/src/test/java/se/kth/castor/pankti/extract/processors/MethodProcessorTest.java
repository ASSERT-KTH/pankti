package se.kth.castor.pankti.extract.processors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.extract.launchers.PanktiLauncher;
import se.kth.castor.pankti.extract.runners.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Check that MethodProcessor correctly identifies candidates in the test resource project")
public class MethodProcessorTest {
    static PanktiMain panktiMain;
    static PanktiLauncher panktiLauncher;
    static MavenLauncher mavenLauncher;
    static CtModel testModel;
    static MethodProcessor methodProcessor;

    @BeforeAll
    public static void setUpLauncherAndModel() throws URISyntaxException {
        mavenLauncher = ModelBuilder.getMavenLauncher();
        panktiLauncher = ModelBuilder.getPanktiLauncher();
        panktiMain = ModelBuilder.getPanktiMain();
        testModel = ModelBuilder.getModel();
        methodProcessor = new MethodProcessor(true);
        testModel.processWith(methodProcessor);
        panktiLauncher.addMetaDataToCandidateMethods(methodProcessor.getCandidateMethods());
    }

    // Test that the project has a POM file in the root path
    @Test
    @DisplayName("The launcher finds the project POM")
    public void testPomPath() {
        assertEquals(panktiMain.getProjectPath().toString() + "/pom.xml",
                mavenLauncher.getPomFile().getPath(),
                "POM file must be present in project path");
    }

    // Test the total number of methods in the project
    @Test
    @DisplayName("The correct number of methods is found")
    public void testMethodCount() {
        assertEquals(893,
                panktiLauncher.countMethods(testModel),
                "Total number of methods in test resource is 893");
    }

    // Test the number of public methods in the project
    @Test
    @DisplayName("The correct number of public methods is found")
    public void testNumberOfPublicMethods() {
        assertEquals(574,
                methodProcessor.publicMethods.size(),
                "Number of public methods in test resource is 574");
    }

    // Test that a method classified as public is indeed public
    @Test
    @DisplayName("A method found to be public has the public modifier")
    public void testPublicMethod() {
        assertTrue(methodProcessor.publicMethods.get(0).getModifiers().contains(ModifierKind.PUBLIC),
                "Method should have a public modifier");
    }

    // Test the number of private methods in the project
    @Test
    @DisplayName("The correct number of private methods is found")
    public void testNumberOfPrivateMethods() {
        assertEquals(188,
                methodProcessor.privateMethods.size(),
                "Number of private methods in test resource is 188");
    }

    // Test that a method classified as private is indeed private
    @Test
    @DisplayName("A method found to be private has the private modifier")
    public void testPrivateMethod() {
        assertTrue(methodProcessor.privateMethods.get(0).getModifiers().contains(ModifierKind.PRIVATE),
                "Method should have a private modifier");
    }

    // Test the number of protected methods in the project
    @Test
    @DisplayName("The correct number of protected methods is found")
    public void testNumberOfProtectedMethods() {
        assertEquals(79,
                methodProcessor.protectedMethods.size(),
                "Number of protected methods in test resource is 79");
    }

    // Test that a method classified as protected is indeed protected
    @Test
    @DisplayName("A method found to be protected has the protected modifier")
    public void testProtectedMethod() {
        assertTrue(methodProcessor.protectedMethods.get(0).getModifiers().contains(ModifierKind.PROTECTED),
                "Method should have a protected modifier");
    }

    // Test the number of abstract methods in the project
    @Test
    @DisplayName("The correct number of abstract methods is found")
    public void testNumberOfAbstractMethods() {
        assertEquals(41,
                methodProcessor.abstractMethods.size(),
                "Number of abstract methods in test resource is 41");
    }

    // Test that a method classified as abstract is indeed abstract
    @Test
    @DisplayName("An abstract method is not a candidate")
    public void testAbstractMethods() {
        CtMethod<?> abstractMethod = methodProcessor.abstractMethods.get(0);
        assertTrue(abstractMethod.isAbstract(),
                "Method should be abstract");
        assertFalse(methodProcessor.candidateMethods.contains(abstractMethod),
                "An abstract method is not extracted");
    }

    // Test the number of methods that are classified as deprecated
    @Test
    @DisplayName("The correct number of deprecated methods is found")
    public void testNumberOfDeprecatedMethods() {
        assertEquals(0,
                methodProcessor.deprecatedMethods.size(),
                "Number of deprecated methods in test resource is 0");
    }

    // Test that a method classified as deprecated actually is deprecated or has a deprecated parent
    @Test
    @DisplayName("No method has the @Deprecated annotation")
    public void testDeprecatedMethod() {
        for (CtMethod<?> extractedMethod : methodProcessor.candidateMethods) {
            assertFalse((extractedMethod.hasAnnotation(Deprecated.class) ||
                            extractedMethod.getParent().hasAnnotation(Deprecated.class)),
                    "No method or its parent is deprecated in test resource");
        }
    }

    // Test the number of methods that are empty
    @Test
    @DisplayName("The correct number of empty methods is found")
    public void testNumberOfEmptyMethods() {
        assertEquals(19,
                methodProcessor.emptyMethods.size(),
                "Number of empty methods in test resource is 19");
    }

    // Test that a method classified as empty indeed has no statements, and is not extracted
    @Test
    @DisplayName("An empty method has no statements and is not a candidate")
    public void testEmptyMethod() {
        CtMethod<?> emptyMethod = methodProcessor.emptyMethods.get(0);
        assertFalse(emptyMethod.getBody().getStatements().size() > 0,
                "Method should not have any statements");
        assertFalse(methodProcessor.candidateMethods.contains(emptyMethod),
                "An empty method is not extracted");
    }

    // Test the number of methods whose parents are annotation types
    @Test
    @DisplayName("The correct number of methods in annotation types is found")
    public void testNumberOfAnnotationTypeMethods() {
        assertEquals(2,
                methodProcessor.methodsInAnnotationType.size(),
                "Number of methods in annotation types in test resource is 2");
    }

    // Test the total number of extracted methods found in the test resource
    @Test
    @DisplayName("The correct number of candidate methods is found")
    public void testNumberOfCandidateMethods() {
        assertEquals(408,
                methodProcessor.candidateMethods.size(),
                "Number of extracted methods in test resource 408");
    }
}
