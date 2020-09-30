package se.kth.castor.pankti.extract.processors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.extract.launchers.PanktiLauncher;
import se.kth.castor.pankti.extract.runners.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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
        panktiLauncher.addMetaDataToCandidateMethods(methodProcessor.getCandidateMethods());
        candidateTagger = new CandidateTagger();
        testModel.processWith(candidateTagger);
    }

    // Test the number of extracted methods that return a value
    @Test
    public void testNumberOfMethodsRetuningAValue() {
        assertEquals(214, candidateTagger.methodsReturningAValue.size(),
                "214 extracted methods in test resource should return a value");
    }


    // Test that all extracted methods return a value
    @Test
    public void testAllextractedMethodsReturnAValue() {
        assertEquals(methodProcessor.candidateMethods.size(), candidateTagger.methodsReturningAValue.size(),
                "All extracted methods in test resource should return a value");
    }

    // Test that an extracted method found to return a value actually does so
    @Test
    public void testMethodRetuningAValue() {
        CtMethod<?> methodReturningAValue = candidateTagger.methodsReturningAValue.get(0);
        assertTrue((methodReturningAValue.getElements(new TypeFilter<>(CtReturn.class)).size() > 0 &&
                        !methodReturningAValue.getType().getSimpleName().equals("void") &&
                        (methodReturningAValue.getType().isPrimitive() || !methodReturningAValue.getType().isPrimitive())),
                "Method should have a return statement, and return a primitive or an object, " +
                        "and its return type cannot be void");
    }

    // Test that an extracted method returning a value is tagged as such
    @Test
    public void testTagOfMethodRetuningAValue() {
        CtMethod<?> methodReturningAValue = candidateTagger.methodsReturningAValue.get(0);
        assertTrue((candidateTagger.allMethodTags.get(methodReturningAValue).get("returns")),
                "returns tag should be true for method");
    }

    // Test that no extracted method returns nothing
    @Test
    public void testNumberOfMethodsNotReturningAValue() {
        assertTrue(candidateTagger.methodsNotReturningAValue.isEmpty(),
                "all extracted methods in test resource return a value");
    }

    // Test the number of extracted methods returning a primitive
    @Test
    public void testNumberOfMethodsReturningPrimitives() {
        assertEquals(65,
                candidateTagger.methodsReturningAPrimitive.size(),
                "65 extracted methods in test resource return a primitive value");
    }

    // Test that an extracted method found to return a primitive actually does so
    @Test
    public void testMethodRetuningAPrimitive() {
        CtMethod<?> methodReturningAPrimitive = candidateTagger.methodsReturningAPrimitive.get(0);
        assertTrue((methodReturningAPrimitive.getElements(new TypeFilter<>(CtReturn.class)).size() > 0 &&
                        !methodReturningAPrimitive.getType().getSimpleName().equals("void") &&
                        methodReturningAPrimitive.getType().isPrimitive()),
                "Method should have a return statement, and return a primitive, " +
                        "and its return type cannot be void");
    }

    // Test that an extracted method returning a primitive is tagged as such
    @Test
    public void testTagOfMethodRetuningAPrimitive() {
        CtMethod<?> methodReturningAPrimitive = candidateTagger.methodsReturningAPrimitive.get(0);
        assertTrue((candidateTagger.allMethodTags.get(methodReturningAPrimitive).get("returns_primitives")),
                "returns_primitives tag should be true for method");
    }

    // Test the number of extracted methods not returning a primitive
    @Test
    public void testNumberOfMethodsNotReturningPrimitives() {
        assertEquals(149,
                candidateTagger.methodsReturningAValue.size() -
                        candidateTagger.methodsReturningAPrimitive.size(),
                "149 extracted methods in test resource return an object");
    }

    // Test that an extracted method found to not return a primitive actually does not
    @Test
    public void testMethodRetuningNotAPrimitive() {
        for (CtMethod<?> methodReturningAValue : candidateTagger.methodsReturningAValue) {
            if (!candidateTagger.methodsReturningAPrimitive.contains(methodReturningAValue)) {
                assertTrue((methodReturningAValue.getElements(new TypeFilter<>(CtReturn.class)).size() > 0 &&
                                !methodReturningAValue.getType().getSimpleName().equals("void") &&
                                !methodReturningAValue.getType().isPrimitive()),
                        "Method should have a return statement, and return an object, " +
                                "and its return type cannot be void");
                break;
            }
        }
    }

    // Test that an extracted method not returning a primitive is tagged as such
    @Test
    public void testTagOfMethodNotRetuningAPrimitive() {
        for (CtMethod<?> methodReturningAValue : candidateTagger.methodsReturningAValue) {
            if (!candidateTagger.methodsReturningAPrimitive.contains(methodReturningAValue)) {
                assertFalse(candidateTagger.allMethodTags.get(methodReturningAValue).get("returns_primitives"),
                        "returns_primitives tag should be false for method");
                break;
            }
        }
    }

    // Test the number of extracted methods with if conditions
    @Test
    public void testNumberOfMethodsWithIfConditions() {
        assertEquals(54,
                candidateTagger.methodsWithIfConditions.size(),
                "54 extracted methods in test resource have an if condition");
    }

    // Test that an extracted method found to have if condition(s) actually does so
    @Test
    public void testMethodsWithIfCondition() {
        CtMethod<?> methodWithIfCondition = candidateTagger.methodsWithIfConditions.get(0);
        assertTrue(methodWithIfCondition.getElements(new TypeFilter<>(CtIf.class)).size() > 0,
                "Method should have an if condition");
    }

    // Test that an extracted method having if condition(s) is tagged as such
    @Test
    public void testTagOfMethodWithIfCondition() {
        CtMethod<?> methodWithIfCondition = candidateTagger.methodsWithIfConditions.get(0);
        assertTrue(candidateTagger.allMethodTags.get(methodWithIfCondition).get("ifs"),
                "ifs tag should be true for method");
    }

    // Test the number of extracted methods with conditional operators
    @Test
    public void testNumberOfMethodsWithConditionals() {
        assertEquals(6,
                candidateTagger.methodsWithConditionalOperators.size(),
                "6 extracted methods in test resource use a conditional operator");
    }

    // Test that an extracted method found to have conditional operator(s) actually does so
    @Test
    public void testMethodWithConditionals() {
        CtMethod<?> methodWithIfCondition = candidateTagger.methodsWithConditionalOperators.get(0);
        assertTrue(methodWithIfCondition.getElements(new TypeFilter<>(CtConditional.class)).size() > 0,
                "Method should have a conditional operator");
    }

    // Test that an extracted method with conditional operator(s) is tagged as such
    @Test
    public void testTagOfMethodWithConditionals() {
        CtMethod<?> methodWithConditional = candidateTagger.methodsWithConditionalOperators.get(0);
        assertTrue(candidateTagger.allMethodTags.get(methodWithConditional).get("conditionals"),
                "conditionals tag should be true for method");
    }

    // Test the number of extracted methods with loops
    @Test
    public void testNumberOfMethodsWithLoops() {
        assertEquals(16, candidateTagger.methodsWithLoops.size(),
                "16 extracted method in test resource have a loop");
    }

    // Test the number of extracted methods with switch statements
    @Test
    public void testNumberOfMethodsWithSwitchStatements() {
        assertEquals(1,
                candidateTagger.methodsWithSwitchStatements.size(),
                "1 extracted method in test resource has switch statements");
    }

    // Test that an extracted method found to have switch statement(s) actually does so
    @Test
    public void testMethodWithSwitchStatements() {
        CtMethod<?> methodWithSwitchStatements = candidateTagger.methodsWithSwitchStatements.get(0);
        assertTrue(methodWithSwitchStatements.getElements(new TypeFilter<>(CtSwitch.class)).size() > 0,
                "Method should have a conditional operator");
    }

    // Test that an extracted method with switch statement(s) is tagged as such
    @Test
    public void testTagOfMethodWithSwitchStatements() {
        CtMethod<?> methodWithSwitchStatements = candidateTagger.methodsWithSwitchStatements.get(0);
        assertTrue(candidateTagger.allMethodTags.get(methodWithSwitchStatements).get("switches"),
                "switches tag should be true for method");
    }

    // Test the number of extracted methods with parameters
    @Test
    public void testNumberOfMethodsWithParameters() {
        assertEquals(72,
                candidateTagger.methodsWithParameters.size(),
                "72 extracted methods in test resource have parameters");
    }

    // Test that an extracted method found to have parameters actually does so
    @Test
    public void testMethodWithParameters() {
        CtMethod<?> methodWithParameters = candidateTagger.methodsWithParameters.get(0);
        assertTrue(methodWithParameters.getParameters().size() > 0,
                "Method should have a conditional operator");
    }

    // Test that an extracted method with parameters is tagged as such
    @Test
    public void testTagOfMethodWithParameters() {
        CtMethod<?> methodWithParameters = candidateTagger.methodsWithParameters.get(0);
        assertTrue(candidateTagger.allMethodTags.get(methodWithParameters).get("parameters"),
                "parameters tag should be true for method");
    }

    // Test the number of extracted methods with multiple statements
    @Test
    public void testNumberOfMethodsWithMultipleStatements() {
        assertEquals(89,
                candidateTagger.methodsWithMultipleStatements.size(),
                "89 extracted methods in test resource have multiple statements");
    }

    // Test that an extracted method found to have multiple statements actually does so
    @Test
    public void testMethodWithMultipleStatements() {
        CtMethod<?> methodWithMultipleStatements = candidateTagger.methodsWithMultipleStatements.get(0);
        assertTrue(methodWithMultipleStatements.getBody().getStatements().size() > 0,
                "Method should have multiple statements");
    }

    // Test that an extracted method with multiple statements is tagged as such
    @Test
    public void testTagOfMethodWithMultipleStatements() {
        CtMethod<?> methodWithMultipleStatements = candidateTagger.methodsWithMultipleStatements.get(0);
        assertTrue(candidateTagger.allMethodTags.get(methodWithMultipleStatements).get("multiple_statements"),
                "multiple_statements tag should be true for method");
    }

    // Test the number of methods with local variables
    @Test
    public void testNumberOfMethodsWithLocalVariables() {
        assertEquals(76,
                candidateTagger.methodsWithLocalVariables.size(),
                "76 extracted methods in test resource define local variables");
    }

    // Test that an extracted method found to have local variables actually does so
    @Test
    public void testMethodWithLocalVariables() {
        CtMethod<?> methodWithLocalVariables = candidateTagger.methodsWithLocalVariables.get(0);
        assertTrue(methodWithLocalVariables.getElements(new TypeFilter<>(CtLocalVariable.class)).size() > 0,
                "Method should have local variables");
    }

    // Test that an extracted method defining local variables is tagged as such
    @Test
    public void testTagOfMethodWithLocalVariables() {
        CtMethod<?> methodWithLocalVariables = candidateTagger.methodsWithLocalVariables.get(0);
        assertTrue(candidateTagger.allMethodTags.get(methodWithLocalVariables).get("local_variables"),
                "local_variables tag should be true for method");
    }
}
