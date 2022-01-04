package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.InstrumentedMethod;
import se.kth.castor.pankti.generate.parsers.SerializedObject;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatementList;
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
import java.util.Map;
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
                                                        SerializedObject serializedObject,
                                                        CtClass<?> generatedTestClass,
                                                        InstrumentedMethod method) throws ClassNotFoundException {
        String nestedMethodMap = method.getNestedMethodMap();
        System.out.println("Generating mocking infrastructure for: " + nestedMethodMap);
        // Convert nested invocation map string to map of invocations and return types
        // e.g., se.kth.castor.runner.Bandicoot.saveEarth(java.lang.String) => java.lang.String
        List<String> sanitizedInvocations = MockGeneratorUtil.getListOfInvocationsFromNestedMethodMap(nestedMethodMap);
        List<String> nestedReturnTypes = MockGeneratorUtil.getReturnTypeFromInvocationMap(nestedMethodMap);
        Map<String, String> nestedInvocationReturnTypeMap =
                MockGeneratorUtil.sanitizeNestedInvocationMap(sanitizedInvocations, nestedReturnTypes);

        System.out.println("Number of nested invocations: " + nestedInvocationReturnTypeMap.size());

        // @InjectMocks ReceivingObject receivingObject = new ReceivingObject();
        CtField<?> mockInjectedField = generateFieldToInjectMocksInto(generatedTestClass, method);

        // Clean up base method, remove non-required statements
        baseMethod = MockGeneratorUtil.cleanUpBaseMethodCloneForMocking(baseMethod, mockInjectedField);

        List<CtMethod<?>> generatedTestsWithMocks = new ArrayList<>();

        for (Map.Entry<String, String> invocationReturnType : nestedInvocationReturnTypeMap.entrySet()) {
            CtField<?> mockField = generateAnnotatedMockField(generatedTestClass, invocationReturnType.getKey());
            // Use the generated mock field in a generated test that uses mocks
            for (SerializedObject nested : serializedObject.getNestedSerializedObjects()) {
                if (nested.getInvocationFQN().equals(invocationReturnType.getKey())) {
                    System.out.println("Generating test with mock for " + invocationReturnType.getKey());

                    // Get serialized nested params
                    String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(invocationReturnType.getKey());
                    List<String> paramTypes = MockGeneratorUtil.getParametersOfMockedMethod(mockedMethodWithParams);
                    String nestedParams;
                    if (paramTypes.size() == 1 & paramTypes.get(0).isEmpty()) {
                        nestedParams = "";
                    } else if (MockGeneratorUtil.arePrimitive(paramTypes)) {
                        nestedParams = MockGeneratorUtil.extractParamsOfNestedInvocation(paramTypes, nested);
                    } else {
                        nestedParams = nested.getParamObjects();
                    }

                    // For non-void methods, get serialized nested returned value and generate stub
                    CtStatementList mockitoWhenThenInvocation = null;
                    if (!invocationReturnType.getValue().equals("void")) {
                        String nestedReturned = MockGeneratorUtil.extractReturnedValueOfNestedInvocation(
                                nested, invocationReturnType.getValue());
                        mockitoWhenThenInvocation = createMockitoWhenThenInvocation(
                                invocationReturnType.getKey(), mockField, paramTypes, nestedParams, invocationReturnType.getValue(), nestedReturned);
                    }

                    CtInvocation mockitoVerifyInvocation = createMockitoVerifyInvocation(invocationReturnType.getKey(), mockField, paramTypes, nestedParams);
                    String mockedMethodNameAndParams = MockGeneratorUtil.getMockedMethodWithParams(invocationReturnType.getKey());
                    String mockedMethodName = MockGeneratorUtil.getMockedMethodName(mockedMethodNameAndParams);
                    CtMethod<?> testWithMock = generateTestWithMock(baseMethod.clone(), mockedMethodName, mockitoVerifyInvocation, mockitoWhenThenInvocation, nested.getUUID());
                    generatedTestsWithMocks.add(testWithMock);
                }
            }
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

    private CtField<?> generateAnnotatedMockField(CtClass<?> generatedTestClass,
                                                  String invocation) throws ClassNotFoundException {
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

    private CtStatementList createMockitoWhenThenInvocation(String invocation,
                                                            CtField<?> mockField,
                                                            List<String> paramTypes,
                                                            String nestedParams,
                                                            String returnType,
                                                            String nestedReturned) throws ClassNotFoundException {
        // when(mockObject.mockedMethod(param1, param2, ...)).thenReturn(returned);
        String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(invocation);

        CtStatementList whenThenStatements = factory.createStatementList();

        if (nestedParams.contains("<object-array>")) {
            whenThenStatements = MockGeneratorUtil.createParamVariableAndParse(paramTypes, nestedParams);
            nestedParams = MockGeneratorUtil.getNestedParamsArgumentForInvocations(paramTypes);
        }

        if (nestedReturned != null & !MockGeneratorUtil.arePrimitive(List.of(returnType))) {
            CtStatementList returnStatements = MockGeneratorUtil.createReturnedVariableAndParse(returnType, nestedReturned);
            for (int i = 0; i < returnStatements.getStatements().size(); i++) {
                whenThenStatements.addStatement(returnStatements.getStatement(i));
            }
            nestedReturned = "nestedReturnedObject";
        }

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
                        nestedParams))));

        // Mockito.when(mockField.mockedMethod(param1, param2)).thenReturn(returned)
        CtExecutableReference<?> executableReferenceForMockitoThen = factory.createExecutableReference();
        executableReferenceForMockitoThen.setSimpleName("thenReturn");
        CtInvocation mockitoThenReturnInvocation = factory.createInvocation();
        mockitoThenReturnInvocation.setExecutable(executableReferenceForMockitoThen);
        mockitoThenReturnInvocation.setTarget(mockitoWhenInvocation);
        if (nestedReturned == null) {
            mockitoThenReturnInvocation.setArguments(List.of(factory.createLiteral(null)));
        } else {
            mockitoThenReturnInvocation.setArguments(List.of(factory.createCodeSnippetExpression(nestedReturned)));
        }
        whenThenStatements.addStatement(mockitoThenReturnInvocation);

        return whenThenStatements;
    }

    private CtInvocation createMockitoVerifyInvocation(String invocation,
                                                       CtField<?> mockField,
                                                       List<String> paramTypes,
                                                       String nestedParams) throws ClassNotFoundException {
        if (nestedParams.contains("<object-array>")) {
            nestedParams = MockGeneratorUtil.getNestedParamsArgumentForInvocations(paramTypes);
        }
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
        mockedMethodInvocation.setArguments(List.of(factory.createCodeSnippetExpression(nestedParams)));
        return mockedMethodInvocation;
    }

    private CtMethod<?> generateTestWithMock(CtMethod<?> testWithMock,
                                             String mockedMethodName,
                                             CtInvocation<?> mockitoVerifyInvocation,
                                             CtStatementList mockitoWhenThenInvocation,
                                             String uuid) {
        // when(mockedObject.mockedMethod(<params>)).thenReturn(<returned-object>);
        // assertEquals(<returned-object>, receivingObject.targetMethod(<params>));
        // verify(mockedObject).mockedMethod
        testWithMock.setSimpleName("testWithMock" + mockedMethodName.substring(0, 1).toUpperCase() + mockedMethodName.substring(1) + "_" + uuid.replace("-", ""));
        if (mockitoWhenThenInvocation != null)
            testWithMock.getBody().insertBegin(mockitoWhenThenInvocation);
        testWithMock.getBody().insertEnd(mockitoVerifyInvocation);
        return testWithMock;
    }
}
