package se.kth.castor.pankti.generate.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestGeneratorUtilTest {
    static Factory factory;
    static CtMethod<?> testMethodForTesting;

    @BeforeAll
    public static void createATestMethod() {
        Launcher launcher = new Launcher();
        launcher.addInputResource("src/test/resources/example/");
        CtModel model = launcher.buildModel();
        factory = model.getRootPackage().getFactory();
        testMethodForTesting = factory.createMethod();
        testMethodForTesting.setSimpleName("aTestMethod");
    }

    @Test
    public void testThatTheCorrectParamListPostfixIsPrepared() {
        assertEquals("", TestGeneratorUtil.getParamListPostFix(new ArrayList<>()));
        String postfix = TestGeneratorUtil.getParamListPostFix(List.of("java.lang.String",
                "se.kth.castor.rick.Roll", "byte[]"));
        assertEquals("_java.lang.String,se.kth.castor.rick.Roll,byte[]", postfix);
    }

    @Test
    public void testThatGeneratedDeserializationMethodIsCorrectForStringSource() {
        TestGeneratorUtil testGeneratorUtil = new TestGeneratorUtil();
        CtMethod<?> generatedDeserializedMethodForString = testGeneratorUtil.generateDeserializationMethod(
                factory, "String");
        CtMethod<?> generatedDeserializedMethodForFile = testGeneratorUtil.generateDeserializationMethod(
                factory, "File");
        assertEquals(1, generatedDeserializedMethodForString.getBody().getStatements().size());
        assertEquals(1, generatedDeserializedMethodForFile.getBody().getStatements().size());
        assertEquals(0, generatedDeserializedMethodForString.getParameters().size(),
                "The parameter is specified later in TestGenerator");
        assertEquals(0, generatedDeserializedMethodForFile.getParameters().size(),
                "The parameter is specified later in TestGenerator");
        assertEquals("deserializeObjectFromString", generatedDeserializedMethodForString.getSimpleName());
        assertEquals("deserializeObjectFromFile", generatedDeserializedMethodForFile.getSimpleName());
        assertEquals("java.lang.T", generatedDeserializedMethodForString.getType().getQualifiedName());
        assertEquals("java.lang.T", generatedDeserializedMethodForFile.getType().getQualifiedName());
    }

    @Test
    public void testThatParametersAreCorrectlyIdentifiedAsBeingPrimitive() {
        TestGeneratorUtil testGeneratorUtil = new TestGeneratorUtil();
        CtMethod<?> methodToTestParams = factory.createMethod();
        CtParameter<?> intParam = factory.createParameter().setType(factory.createReference("int"));
        CtParameter<?> boolParam = factory.createParameter().setType(factory.createReference("boolean"));
        methodToTestParams.setParameters(List.of(intParam, boolParam));
        assertTrue(testGeneratorUtil.allMethodParametersArePrimitive(methodToTestParams));
        CtParameter<?> stringParam = factory.createParameter().setType(factory.createCtTypeReference(String.class));
        CtParameter<?> domainParam = factory.createParameter().setType(factory.createReference("se.kth.castor.rick.Roll"));
        methodToTestParams.addParameter(stringParam);
        methodToTestParams.addParameter(domainParam);
        assertFalse(testGeneratorUtil.allMethodParametersArePrimitive(methodToTestParams));
    }
}
