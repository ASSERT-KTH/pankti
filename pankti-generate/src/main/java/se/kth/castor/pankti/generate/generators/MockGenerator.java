package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.InstrumentedMethod;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MockGenerator {
    static Factory factory;

    private static final String JUNIT_EXTEND_WITH_REFERENCE = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String MOCKITO_EXTENSION_REFERENCE = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String MOCKITO_REFERENCE = "org.mockito.Mockito";
    private static final String MOCKITO_INJECT_MOCKS_REFERENCE = "org.mockito.InjectMocks";
    private static final String MOCKITO_MOCK_REFERENCE = "org.mockito.Mock";

    private void addFieldToGeneratedClass(CtClass<?> generatedClass, CtField<?> generatedField) {
        if (generatedClass.getFields().stream()
                .noneMatch(ctField -> ctField.getSimpleName().equals(generatedField.getSimpleName())))
            generatedClass.addFieldAtTop(generatedField);
    }

    /**
     * Initialize MockGenerator with factory
     *
     * @param factory
     */
    MockGenerator(Factory factory) {
        MockGenerator.factory = factory;
        MockGeneratorUtil.factory = factory;
    }

    /**
     * Generates a Mockito-specific annotation for the given reference (such as @Mock, @InjectMocks, @ExtendWith)
     *
     * @param annotationReference
     * @return generated annotation
     * @throws ClassNotFoundException
     */
    private CtAnnotation<?> generateMockitoAnnotation(String annotationReference) throws ClassNotFoundException {
        return factory.createAnnotation(
                factory.createCtTypeReference(Class.forName(annotationReference)));
    }

    /**
     * Adds the @ExtendWith(MockitoExtension.class) annotation to generated test class
     *
     * @param generatedClass
     * @throws ClassNotFoundException
     */
    public void addAnnotationToGeneratedClass(CtClass<?> generatedClass) throws ClassNotFoundException {
        CtAnnotation<?> extendWithAnnotation = generateMockitoAnnotation(JUNIT_EXTEND_WITH_REFERENCE);
        extendWithAnnotation.addValue("value", Class.forName(MOCKITO_EXTENSION_REFERENCE));
        generatedClass.addAnnotation(extendWithAnnotation);
    }

    /**
     * Finds a list of types from the project model, given a name
     *
     * @param typeToFind
     * @return a list of types found for the given name
     */
    private List<CtTypeReference<?>> findTypeFromModel(final String typeToFind) {
        List<CtTypeReference<?>> foundTypes = factory.getModel()
                .getAllTypes()
                .stream()
                .filter(ctType -> ctType.getQualifiedName().equals(typeToFind))
                .map(ctType -> ctType.getReference())
                .collect(Collectors.toList());
        return foundTypes;
    }

    /**
     * Generates @Mock, @InjectMocks fields, as well as @Test methods that use these mocks
     *
     * @param generatedTestClass is the test class being generated
     * @param method             is the method to generate tests for
     * @return a list of fields that have been generated (TODO: might be void)
     * @throws ClassNotFoundException
     */
    public List<CtMethod<?>> generateMockInfrastructure(CtMethod<?> baseMethod,
                                                        CtClass<?> generatedTestClass,
                                                        InstrumentedMethod method) throws ClassNotFoundException {
        String nestedMethodMap = method.getNestedMethodMap();
        System.out.println("Generating mocking infrastructure for: " + nestedMethodMap);
        // Convert invocation map string to list of strings
        List<String> sanitizedInvocations = MockGeneratorUtil.getListOfInvocationsFromNestedMethodMap(nestedMethodMap);
        System.out.println("Number of nested invocations: " + sanitizedInvocations.size());

        // @InjectMocks ReceivingObject receivingObject = new ReceivingObject();
        CtField<?> mockInjectedField = generateFieldToInjectMocksInto(generatedTestClass, method);
        List<CtMethod<?>> generatedTestsWithMocks = new ArrayList<>();

        // Clean up base method, remove non-required statements
        baseMethod = MockGeneratorUtil.cleanUpBaseMethodCloneForMocking(baseMethod, mockInjectedField);

        for (String invocation : sanitizedInvocations) {
            CtField<?> mockField = generateAnnotatedMockField(generatedTestClass, invocation);
            // use the generated mock field in a generated test that uses mocks
            System.out.println("Generating test with mock for " + invocation);
            CtInvocation mockitoWhenThenInvocation = createMockitoWhenThenInvocation(invocation, mockField);
            CtInvocation mockitoVerifyInvocation = createMockitoVerifyInvocation(invocation, mockField);
            String mockedMethodNameAndParams = MockGeneratorUtil.getMockedMethodWithParams(invocation);
            String mockedMethodName = MockGeneratorUtil.getMockedMethodName(mockedMethodNameAndParams);
            CtMethod<?> testWithMock = generateTestWithMock(baseMethod.clone(), mockedMethodName, mockitoVerifyInvocation, mockitoWhenThenInvocation);
            generatedTestsWithMocks.add(testWithMock);
        }
        System.out.println("Generated " + generatedTestsWithMocks.size() + " test(s) with mocks");
        return generatedTestsWithMocks;
    }

    private CtField<?> generateFieldToInjectMocksInto(CtClass<?> generatedClass,
                                                      InstrumentedMethod method) throws ClassNotFoundException {
        CtField<?> injectMockField = factory.createCtField(
                method.getParentSimpleName().toLowerCase(),
                findTypeFromModel(method.getParentFQN()).get(0),
                String.format("new %s()", method.getParentSimpleName())
        );
        injectMockField.addAnnotation(generateMockitoAnnotation(MOCKITO_INJECT_MOCKS_REFERENCE));
        addFieldToGeneratedClass(generatedClass, injectMockField);
        return injectMockField;
    }

    private CtField<?> generateAnnotatedMockField(CtClass<?> generatedTestClass, String invocation) throws ClassNotFoundException {
        String declaringTypeToMock = MockGeneratorUtil.getDeclaringTypeToMock(invocation);

        CtTypeReference mockFieldType = findTypeFromModel(declaringTypeToMock).get(0);
        CtField<?> mockField = factory.createField();
        mockField.setSimpleName(String.format("mock%s", mockFieldType.getSimpleName()));
        mockField.setType(mockFieldType);
        mockField.setModifiers(Set.of(ModifierKind.PRIVATE));
        mockField.addAnnotation(generateMockitoAnnotation(MOCKITO_MOCK_REFERENCE));
        addFieldToGeneratedClass(generatedTestClass, mockField);
        return mockField;
    }

    private CtInvocation createMockitoWhenThenInvocation(String invocation, CtField<?> mockField) throws ClassNotFoundException {
        // when(mockObject.mockedMethod(param1, param2, ...)).thenReturn(returned);
        String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(invocation);
        List<String> params = MockGeneratorUtil.getParametersOfMockedMethod(mockedMethodWithParams);
        String paramExecutables = MockGeneratorUtil.convertParamsToMockitoArgumentMatchers(params).toString();

        // Mockito.when()
        CtInvocation mockitoWhenInvocation = factory.createInvocation();
        CtExecutableReference<?> executableReferenceForMockitoWhen = factory.createExecutableReference();
        executableReferenceForMockitoWhen.setSimpleName("when");
        executableReferenceForMockitoWhen.setStatic(true);
        executableReferenceForMockitoWhen.setDeclaringType(factory.createCtTypeReference(Class.forName(MOCKITO_REFERENCE)));
        mockitoWhenInvocation.setExecutable(executableReferenceForMockitoWhen);
        mockitoWhenInvocation.setTarget(factory.createTypeAccess(factory.createCtTypeReference(Class.forName(MOCKITO_REFERENCE))));

        // Mockito.when(mockField.mockedMethod(param1, param2))
        mockitoWhenInvocation.setArguments(List.of(factory.createCodeSnippetExpression(
                String.format("%s.%s(%s)",
                        mockField.getSimpleName(),
                        MockGeneratorUtil.getMockedMethodName(mockedMethodWithParams),
                        paramExecutables.substring(1, paramExecutables.length() - 1)))));

        // Mockito.when(mockField.mockedMethod(param1, param2)).thenReturn(returned)
        CtExecutableReference<?> executableReferenceForMockitoThen = factory.createExecutableReference();
        executableReferenceForMockitoThen.setSimpleName("thenReturn");
        CtInvocation mockitoThenReturnInvocation = factory.createInvocation();
        mockitoThenReturnInvocation.setExecutable(executableReferenceForMockitoThen);
        mockitoThenReturnInvocation.setTarget(mockitoWhenInvocation);
        mockitoThenReturnInvocation.setArguments(List.of(factory.createCodeSnippetExpression("any()")));
        return mockitoThenReturnInvocation;
    }

    private CtInvocation createMockitoVerifyInvocation(String invocation, CtField<?> mockField) throws ClassNotFoundException {
        // assertEquals(42, calculator.getSum(21, 21);
        // verify(mockObject, times(1)).mockedMethod(any());
        // Mockito.verify(mockedObject)
        CtExecutableReference<?> executableReferenceForMockitoVerify = factory.createExecutableReference();
        executableReferenceForMockitoVerify.setSimpleName("verify");
        executableReferenceForMockitoVerify.setStatic(true);
        executableReferenceForMockitoVerify.setDeclaringType(factory.createCtTypeReference(Class.forName(MOCKITO_REFERENCE)));
        CtExpression<?> mockedFieldExpression = factory.createCodeSnippetExpression(mockField.getSimpleName());
        CtInvocation mockitoVerifyInvocation = factory.createInvocation();
        mockitoVerifyInvocation.setExecutable(executableReferenceForMockitoVerify);
        mockitoVerifyInvocation.setTarget(factory.createTypeAccess(factory.createCtTypeReference(Class.forName(MOCKITO_REFERENCE))));
        mockitoVerifyInvocation.setArguments(Arrays.asList(mockedFieldExpression));

        // Mockito.verify(mockedObject).mockedMethod(param1, param2, ...)
        String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(invocation);
        CtExecutableReference<?> executableReferenceForMockedMethod = factory.createExecutableReference();
        executableReferenceForMockedMethod.setSimpleName(MockGeneratorUtil.getMockedMethodName(mockedMethodWithParams));
        CtInvocation mockedMethodInvocation = factory.createInvocation();
        mockedMethodInvocation.setExecutable(executableReferenceForMockedMethod);
        mockedMethodInvocation.setTarget(mockitoVerifyInvocation);
        List<String> params = MockGeneratorUtil.getParametersOfMockedMethod(mockedMethodWithParams);
        List<CtExecutableReference<?>> paramExecutables = MockGeneratorUtil.convertParamsToMockitoArgumentMatchers(params);
        List<CtExpression<?>> paramExpressions = new ArrayList<>();
        for (CtExecutableReference<?> paramExecutable : paramExecutables) {
            paramExpressions.add(factory.createCodeSnippetExpression(paramExecutable.toString()));
        }
        mockedMethodInvocation.setArguments(paramExpressions);
        return mockedMethodInvocation;
    }

    private CtMethod<?> generateTestWithMock(CtMethod<?> testWithMock,
                                             String mockedMethodName,
                                             CtInvocation<?> mockitoVerifyInvocation,
                                             CtInvocation<?> mockitoWhenThenInvocation) {
        // when(mockedObject.mockedMethod(<params>)).thenReturn(<returned-object>);
        // assertEquals(<returned-object>, receivingObject.targetMethod(<params>));
        // verify(mockedObject).mockedMethod
        testWithMock.setSimpleName("testWithMock" + mockedMethodName.substring(0, 1).toUpperCase() + mockedMethodName.substring(1));
        testWithMock.getBody().insertBegin(mockitoWhenThenInvocation);
        testWithMock.getBody().insertEnd(mockitoVerifyInvocation);
        return testWithMock;
    }
}
