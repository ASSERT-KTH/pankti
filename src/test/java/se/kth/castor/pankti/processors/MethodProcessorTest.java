package se.kth.castor.pankti.processors;

import se.kth.castor.pankti.launchers.PanktiLauncher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.runner.PanktiMain;
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
                "An abstract method is not pure");
    }

    // Test the number of methods throwing exceptions in the project
    @Test
    public void testNumberOfMethodsThrowingExceptions() {
        assertEquals(95,
                methodProcessor.methodsThrowingExceptions.size(),
                "Number of methods in test resource that throw exceptions is 95");
    }

    // Test that a method classified as throwing exceptions actually does so, and is not classified as pure
    @Test
    public void testMethodThrowingExceptions() {
        CtMethod<?> methodThrowingExceptions = methodProcessor.methodsThrowingExceptions.get(0);
        assertFalse((methodThrowingExceptions.getThrownTypes().isEmpty() ||
                        methodThrowingExceptions.getElements(new TypeFilter<>(CtThrow.class)).size() == 0),
                "Method should throw exceptions");
        assertFalse(methodProcessor.candidateMethods.contains(methodThrowingExceptions),
                "A method throwing exceptions is not pure");
    }

    // Test the number of methods that modify fields in the project
    @Test
    public void testNumberOfMethodsModifyingFields() {
        assertEquals(19,
                methodProcessor.methodsWithFieldAssignments.size(),
                "Number of methods in test resource that modify fields is 19");
    }

    // Test that a method classified as modifying fields actually does so, and is not classified as pure
    @Test
    public void testMethodsModifyingFields() {
        CtMethod<?> methodModifyingField = methodProcessor.methodsWithFieldAssignments.get(0);
        assertTrue(methodModifyingField.getElements(new TypeFilter<>(CtFieldWrite.class)).size() > 0,
                "Method should write to fields");
        assertFalse(methodProcessor.candidateMethods.contains(methodModifyingField),
                "A method modifying fields is not pure ");
    }

    // Test the number of methods that invoke constructors
    @Test
    public void testNumberOfMethodsInvokingConstructors() {
        assertEquals(3,
                methodProcessor.methodsWithConstructorCalls.size(),
                "Number of methods in test resource that invoke constructors is 3");
    }

    // Test that a method classified as invoking constructors actually does so, and is not classified as pure
    @Test
    public void testMethodInvokingConstructors() {
        CtMethod<?> methodWithConstructorInvocations = methodProcessor.methodsWithConstructorCalls.get(0);
        assertTrue(methodWithConstructorInvocations.getElements(new TypeFilter<>(CtConstructorCall.class)).size() > 0,
                "Method should invoke constructors");
        assertFalse(methodProcessor.candidateMethods.contains(methodWithConstructorInvocations),
                "A method invoking constructors is not pure");
    }

    // Test the number of methods that invoke other methods
    @Test
    public void testNumberOfMethodsInvokingOtherMethods() {
        assertEquals(650,
                methodProcessor.methodsWithInvocations.size(),
                "Number of methods in test resource that invoke other methods is 650");
    }

    // Test that a method classified as invoking methods actually does so, and is not classified as pure
    @Test
    public void testMethodInvokingOtherMethods() {
        CtMethod<?> methodWithInvocations = methodProcessor.methodsWithInvocations.get(0);
        assertTrue(methodWithInvocations.getElements(new TypeFilter<>(CtInvocation.class)).size() > 0,
                "Method should invoke another method");
        assertFalse(methodProcessor.candidateMethods.contains(methodWithInvocations),
                "A method invoking other methods is not pure");
    }

    // Test the number of methods that are classified as synchronized
    @Test
    public void testNumberOfSynchronizedMethods() {
        assertEquals(15,
                methodProcessor.methodsWithSynchronization.size(),
                "Number of methods in test resource with synchronization is 15");
    }

    // Test that a method classified as synchronized actually has synchronized blocks or a synchronized modifier
    // and that it is not classified as pure
    @Test
    public void testSynchronizedMethod() {
        CtMethod<?> methodWithSynchronization = methodProcessor.methodsWithSynchronization.get(0);
        assertTrue(methodWithSynchronization.getModifiers().contains(ModifierKind.SYNCHRONIZED) ||
                        methodWithSynchronization.getElements(new TypeFilter<>(CtSynchronized.class)).size() > 0,
                "Method should have a synchronized modifier or a synchronized block");
        assertFalse(methodProcessor.candidateMethods.contains(methodWithSynchronization),
                "A method with synchronization is not pure");
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
        for (CtMethod<?> pureMethod : methodProcessor.candidateMethods) {
            assertFalse((pureMethod.hasAnnotation(Deprecated.class) ||
                            pureMethod.getParent().hasAnnotation(Deprecated.class)),
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

    // Test that a method classified as empty indeed has no statements, and is not classified as pure
    @Test
    public void testEmptyMethod() {
        CtMethod<?> emptyMethod = methodProcessor.emptyMethods.get(0);
        assertFalse(emptyMethod.getBody().getStatements().size() > 0,
                "Method should not have any statements");
        assertFalse(methodProcessor.candidateMethods.contains(emptyMethod),
                "An empty method is not pure");
    }

    // Test the number of methods that modify array arguments
    @Test
    public void testNumberOfMethodsModifyingArrayArguments() {
        assertEquals(0,
                methodProcessor.methodsModifyingArrayArguments.size(),
                "Number of methods in test resource that modify array arguments is 0");
    }

    // Test that a method classified as modifying non-local variables actually does so
    @Test
    public void testNumberOfMethodsModifyingNonLocalVariables() {
        assertEquals(0,
                methodProcessor.methodsModifyingNonLocalVariables.size(),
                "Number of methods in test resource that modify non-local variables is 188");
    }

    // Test the number of methods whose parents are annotation types
    @Test
    public void testNumberOfAnnotationTypeMethods() {
        assertEquals(2,
                methodProcessor.methodsInAnnotationType.size(),
                "Number of methods in annotation types in test resource is 2");
    }

    // Test the total number of pure methods found in the test resource
    @Test
    public void testNumberOfCandidateMethods() {
        assertEquals(105,
                methodProcessor.candidateMethods.size(),
                "Number of pure methods in test resource 105");
    }
}
