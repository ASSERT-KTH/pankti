package se.kth.castor.pankti.generate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.generate.data.InstrumentedMethod;
import se.kth.castor.pankti.generate.generators.TestGenerator;
import se.kth.castor.pankti.generate.parsers.CSVFileParser;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests verify that the project model has expected types,
 * overloaded methods are resolved, test generation targets
 * are identified, and that tests are generated for them
 */
public class TestGeneratorTest {
    static Launcher launcher = new Launcher();
    static CtModel model;
    static TestGenerator testGeneratorWithoutMocks;
    static TestGenerator testGeneratorWithMocks;
    static List<InstrumentedMethod> instrumentedMethods;

    @BeforeAll
    public static void setup() {
        launcher.addInputResource("src/test/resources/example/");
        model = launcher.buildModel();
        testGeneratorWithoutMocks = new TestGenerator("xml", launcher, false);
        testGeneratorWithMocks = new TestGenerator("xml", launcher, true);
        instrumentedMethods = CSVFileParser.parseCSVFile(
                "src/test/resources/example-object-data/invoked-methods.csv");
    }

    @Test
    public void testThatAllTypesArePresentInTheModel() {
        assertEquals(4, launcher.getModel().getAllTypes().size(),
                "The model should have four types");
    }

    @Test
    public void testThatTypesToProcessAreFound() {
        assertEquals(4, testGeneratorWithMocks.getTypesToProcess(model).size());
        assertEquals("se.kth.castor.pankti.generate.example.ClassOne",
                testGeneratorWithMocks.getTypesToProcess(model).get(0).getQualifiedName());
    }

    @Test
    public void getListOfInstrumentedMethodsFromInvokedCSV() {
        assertEquals(9, instrumentedMethods.size());
    }

    @Test
    public void testThatOverloadedMethodsAreResolved() {
        InstrumentedMethod overloadedInstrumentedMethod = instrumentedMethods.stream()
                .filter(im -> im.getMethodName().equals("methodWithNoNestedInvocation")).findFirst().get();
        assertEquals("methodWithNoNestedInvocation", overloadedInstrumentedMethod.getMethodName());

        Optional<CtType<?>> typeWithOverloadedMethod =
                testGeneratorWithMocks.getTypesToProcess(model).stream()
                        .filter(ctType -> ctType.getSimpleName().equals("ClassOne"))
                        .findFirst();
        assertTrue(typeWithOverloadedMethod.isPresent());

        List<CtMethod<?>> methodsByName = typeWithOverloadedMethod.get().getMethodsByName(overloadedInstrumentedMethod.getMethodName());

        Map.Entry<CtMethod<?>, Boolean> methodAndOverload = testGeneratorWithMocks.findMethodToGenerateTestMethodsFor(methodsByName, overloadedInstrumentedMethod);
        assertEquals("java.lang.String",
                methodAndOverload.getKey().getParameters().get(0).getType().getQualifiedName());
        assertTrue(methodAndOverload.getValue());
    }
}
