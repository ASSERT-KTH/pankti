package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.data.InstrumentedMethod;
import se.kth.castor.pankti.generate.util.MethodInvocationUtil;
import se.kth.castor.pankti.generate.data.NestedInvocation;
import se.kth.castor.pankti.generate.data.SerializedObject;
import se.kth.castor.pankti.generate.util.MockGeneratorUtil;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

import java.util.*;

public class MockGenerator {
    static Factory factory;
    static int counter;
    private static final String JUNIT_EXTEND_WITH_REFERENCE = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String MOCKITO_EXTENSION_REFERENCE = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String JUNIT_JUPITER_DISABLED_REFERENCE = "org.junit.jupiter.api.Disabled";
    private static final String MOCKITO_REFERENCE = "org.mockito.Mockito";

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

    private List<String> getParamTypes(NestedInvocation nestedInvocation) {
        String mockedMethodWithParams = MethodInvocationUtil.getMethodWithParamsFromInvocationFQN(
                nestedInvocation.getInvocation());
        return MethodInvocationUtil.getMethodParams(mockedMethodWithParams);
    }

    private static String prepareNestedParams(List<String> paramTypes,
                                              SerializedObject nestedSerializedObject) {
        // Get serialized nested params
        String nestedParams;
        if (paramTypes.size() == 1 & paramTypes.get(0).isEmpty()) {
            nestedParams = "";
        } else if (MockGeneratorUtil.arePrimitiveOrString(paramTypes)) {
            nestedParams = MockGeneratorUtil.extractParamsOfNestedInvocation(paramTypes, nestedSerializedObject);
        } else {
            nestedParams = nestedSerializedObject.getParamObjects();
        }
        return nestedParams;
    }


    private CtStatementList prepareWhenThenInvocation(NestedInvocation nestedInvocation,
                                                      SerializedObject nestedSerializedObject,
                                                      String mockVariableTypeSimple,
                                                      List<String> paramTypes,
                                                      String nestedParams) throws ClassNotFoundException {
        // For non-void methods, get serialized nested returned value and generate stub
        CtStatementList mockitoWhenThenInvocation = null;
        if (!nestedInvocation.getInvocationReturnType().equals("void")) {
            String nestedReturned = MockGeneratorUtil.extractReturnedValueOfNestedInvocation(
                    nestedSerializedObject, nestedInvocation.getInvocationReturnType());
            mockitoWhenThenInvocation = createMockitoWhenThenInvocation(
                    nestedInvocation.getInvocation(), mockVariableTypeSimple, paramTypes, nestedParams, nestedInvocation.getInvocationReturnType(), nestedReturned);
        }
        return mockitoWhenThenInvocation;
    }

    private CtMethod<?> getEverythingTogetherAndGenerateTest(NestedInvocation nestedInvocation,
                                                             SerializedObject nestedSerializedObject,
                                                             String mockVariableTypeSimple,
                                                             String mockVariableName,
                                                             CtMethod<?> updatedBaseMethod,
                                                             String targetFieldName) throws ClassNotFoundException {
        List<String> paramTypes = getParamTypes(nestedInvocation);
        String nestedParams = "";
        if (nestedSerializedObject != null) {
            nestedParams = prepareNestedParams(paramTypes, nestedSerializedObject);
        }
        CtStatementList mockitoWhenThenInvocation = null;
        if (nestedSerializedObject != null) {
            mockitoWhenThenInvocation = prepareWhenThenInvocation(nestedInvocation,
                    nestedSerializedObject, mockVariableTypeSimple, paramTypes, nestedParams);
        }
        CtInvocation mockitoVerifyInvocation = createMockitoVerifyInvocation(nestedInvocation.getInvocation(),
                mockVariableName, paramTypes, nestedParams, nestedSerializedObject);
        String mockedMethodNameAndParams = MethodInvocationUtil.getMethodWithParamsFromInvocationFQN(nestedInvocation.getInvocation());
        String mockedMethodName = MethodInvocationUtil.getMethodName(mockedMethodNameAndParams);
        CtMethod<?> testWithMock =
                generateTestWithMock(updatedBaseMethod.clone(),
                        mockedMethodName,
                        mockitoVerifyInvocation,
                        mockitoWhenThenInvocation,
                        nestedSerializedObject == null ? "" : nestedSerializedObject.getUUID(),
                        targetFieldName);
        return testWithMock;
    }

    private Set<CtMethod<?>> handleInvocationOnFields(SerializedObject serializedObject,
                                                      InstrumentedMethod targetMethod,
                                                      NestedInvocation nestedInvocation,
                                                      CtMethod<?> baseMethod,
                                                      CtClass<?> generatedTestClass,
                                                      String mockVariableName,
                                                      String mockVariableTypeFQN,
                                                      String mockVariableTypeSimple,
                                                      SerializedObject nestedSerializedObject) throws ClassNotFoundException {
        CtMethod<?> updatedBaseMethod;
        Set<CtMethod<?>> generatedTestsWithMocks = new LinkedHashSet<>();
        String receivingObjectType = serializedObject.getObjectType(serializedObject.getReceivingObject());
        String receivingObjectTypeSimple = MethodInvocationUtil.getDeclaringTypeSimpleNameFromFQN(receivingObjectType);
        String targetMethodType = targetMethod.getParentFQN();

        String helperMethodName;

        Map<String, String> fieldVisibilityMap = nestedInvocation.getInvocationFieldsVisibilityMap();

        for (Map.Entry<String, String> entry : fieldVisibilityMap.entrySet()) {
            String targetFieldName = nestedInvocation.getTargetFieldName(entry);
            boolean targetFieldIsPrivate = nestedInvocation.isTargetFieldPrivate(entry);

            // See processPage in PDFTextStripper extends LegacyPDFStreamEngine with private pageSize
            if (receivingObjectType.equals(targetMethodType) || !targetFieldIsPrivate) {

                // Clean up base method, remove non-required statements
                updatedBaseMethod = MockGeneratorUtil.cleanUpBaseMethodCloneForMocking(baseMethod,
                        MockGeneratorUtil.arePrimitiveOrString(List.of(targetMethod.getReturnType())),
                        targetMethod.getReturnType().equals("void"));

                if (nestedInvocation.isTargetFieldPrivate(entry)) {
                    helperMethodName = String.format("insertPrivateMockField%sIn%s",
                            targetFieldName, receivingObjectTypeSimple);
                } else {
                    helperMethodName = String.format("insertMockField%sIn%s",
                            targetFieldName, receivingObjectTypeSimple);
                }

                if (generatedTestClass.getMethodsByName(helperMethodName).size() == 0) {
                    CtMethod<?> helperMethodForMockFieldInjection =
                            MockGeneratorUtil.generateHelperMethodForMockFieldInjection(
                                    helperMethodName,
                                    mockVariableTypeFQN,
                                    mockVariableTypeSimple,
                                    mockVariableName,
                                    targetFieldName,
                                    targetFieldIsPrivate,
                                    receivingObjectType);
                    generatedTestClass.addMethod(helperMethodForMockFieldInjection);
                }

                CtStatement addMockFieldToTest = factory.createCodeSnippetStatement(String.format(
                        "%s %s = %s(receivingObject)",
                        mockVariableTypeSimple, mockVariableName, helperMethodName
                ));

                if (updatedBaseMethod.getBody().getStatements().stream()
                        .noneMatch(s -> s.toString().equals(addMockFieldToTest.toString()))) {
                    updatedBaseMethod.getBody().getLastStatement().insertBefore(addMockFieldToTest);
                }

                CtMethod<?> testWithMock = getEverythingTogetherAndGenerateTest(nestedInvocation,
                        nestedSerializedObject, mockVariableTypeSimple, mockVariableName,
                        updatedBaseMethod, targetFieldName);

                generatedTestsWithMocks.add(testWithMock);
            } else {
                System.out.println("target method type is not equal to receiving object type and " +
                        "target field is private - " + targetMethodType);
            }
        }
        return generatedTestsWithMocks;
    }

    private CtMethod<?> handleInvocationOnParameter(InstrumentedMethod targetMethod,
                                                    CtMethod<?> baseMethod,
                                                    String mockVariableTypeSimple,
                                                    String mockVariableTypeFQN,
                                                    String mockVariableName,
                                                    NestedInvocation nestedInvocation,
                                                    SerializedObject nestedSerializedObject) throws ClassNotFoundException {
        CtMethod<?> updatedBaseMethod;
        boolean targetReturnsNonPrimitiveOrVoid = !MockGeneratorUtil.arePrimitiveOrString(
                List.of(targetMethod.getReturnType())) ||
                targetMethod.getReturnType().equals("void");

        // Clean up base method, remove non-required statements
        updatedBaseMethod = MockGeneratorUtil.cleanUpBaseMethodCloneForMocking(baseMethod,
                MockGeneratorUtil.arePrimitiveOrString(List.of(targetMethod.getReturnType())),
                targetMethod.getReturnType().equals("void"));

        updatedBaseMethod.getBody().getLastStatement().insertBefore(
                MockGeneratorUtil.generateLocalVariableForMockParameter(mockVariableTypeSimple));

        updatedBaseMethod = MockGeneratorUtil.updateAssertionForInvocationOnParameters(targetMethod.getParamList(),
                updatedBaseMethod, mockVariableTypeFQN, mockVariableName,
                targetReturnsNonPrimitiveOrVoid);

        CtMethod<?> testWithMock = getEverythingTogetherAndGenerateTest(nestedInvocation,
                nestedSerializedObject, mockVariableTypeSimple, mockVariableName,
                updatedBaseMethod, "");

        return testWithMock;
    }

    /**
     * Generates mocked fields, mocked parameters, helper methods,
     * as well as @Test methods that use these mocks
     *
     * @param generatedTestClass is the test class being generated
     * @param targetMethod       is the method to generate tests for
     * @return a list of fields that have been generated
     * @throws ClassNotFoundException
     */
    public Set<CtMethod<?>> generateMockInfrastructure(CtMethod<?> baseMethod,
                                                       SerializedObject serializedObject,
                                                       CtClass<?> generatedTestClass,
                                                       InstrumentedMethod targetMethod) throws ClassNotFoundException {
        String nestedMethodMap = targetMethod.getNestedMethodMap();
        List<String> sanitizedInvocations = MockGeneratorUtil.sanitizeNestedInvocationMap(nestedMethodMap);
        List<String> nestedReturnTypes = MockGeneratorUtil.getReturnTypeFromInvocationMap(nestedMethodMap);
        List<String> invocationTargetTypes = MockGeneratorUtil.getNestedInvocationTargetTypesFromNestedMethodMap(nestedMethodMap);
        List<Map<String, String>> fieldVisibilityMaps = MockGeneratorUtil.getNestedInvocationTargetFieldVisibilityMap(invocationTargetTypes, nestedMethodMap);

        assert (sanitizedInvocations.size() == nestedReturnTypes.size() &
                nestedReturnTypes.size() == invocationTargetTypes.size() &
                invocationTargetTypes.size() == fieldVisibilityMaps.size());

        List<NestedInvocation> nestedInvocations = new ArrayList<>();
        for (int i = 0; i < sanitizedInvocations.size(); i++) {
            nestedInvocations.add(new NestedInvocation(
                    sanitizedInvocations.get(i), nestedReturnTypes.get(i),
                    invocationTargetTypes.get(i), fieldVisibilityMaps.get(i)));
        }

        Set<CtMethod<?>> generatedTestsWithMocks = new LinkedHashSet<>();

        for (NestedInvocation nestedInvocation : nestedInvocations) {
            String mockVariableTypeFQN = MethodInvocationUtil.getDeclaringTypeFromInvocationFQN(nestedInvocation.getInvocation());
            String mockVariableTypeSimple = MethodInvocationUtil.getDeclaringTypeSimpleNameFromFQN(mockVariableTypeFQN);
            String mockVariableName = "mock" + mockVariableTypeSimple;

            // Use the generated mock field in a generated test that uses mocks
            for (SerializedObject nestedSerializedObject : serializedObject.getNestedSerializedObjects()) {
                if (nestedSerializedObject.getInvocationFQN().equals(nestedInvocation.getInvocation())) {
                    System.out.println("Generating test with mock for " + nestedInvocation);

                    if (nestedInvocation.getInvocationTargetType().equals("FIELD")) {
                        nestedInvocation.setHasCorrespondingSerializedObject();;
                        generatedTestsWithMocks.addAll(handleInvocationOnFields(
                                serializedObject, targetMethod, nestedInvocation, baseMethod,
                                generatedTestClass, mockVariableName, mockVariableTypeFQN,
                                mockVariableTypeSimple, nestedSerializedObject));
                    } else if (nestedInvocation.getInvocationTargetType().equals("PARAMETER")) {
                        nestedInvocation.setHasCorrespondingSerializedObject();
                        generatedTestsWithMocks.add(handleInvocationOnParameter(targetMethod,
                                baseMethod, mockVariableTypeSimple, mockVariableTypeFQN,
                                mockVariableName, nestedInvocation,
                                nestedSerializedObject));
                    }
                }
            }
            // If there is no object profile corresponding to this nested invocation
            if (!nestedInvocation.hasCorrespondingSerializedObject()) {
                System.out.println("No nested invocations found, will generate Mockito.never()");
                if (nestedInvocation.getInvocationTargetType().equals("FIELD")) {
                    generatedTestsWithMocks.addAll(handleInvocationOnFields(serializedObject, targetMethod,
                            nestedInvocation, baseMethod, generatedTestClass,
                            mockVariableName, mockVariableTypeFQN,
                            mockVariableTypeSimple, null));
                } else if (nestedInvocation.getInvocationTargetType().equals("PARAMETER")) {
                    generatedTestsWithMocks.add(handleInvocationOnParameter(targetMethod,
                            baseMethod, mockVariableTypeSimple, mockVariableTypeFQN,
                            mockVariableName, nestedInvocation,
                            null));
                }
            }
        }

        System.out.println("Generated " + generatedTestsWithMocks.size() + " test(s) with mocks");
        return generatedTestsWithMocks;
    }

    private CtStatementList createMockitoWhenThenInvocation(String invocation,
                                                            String mockVariableSimpleName,
                                                            List<String> paramTypes,
                                                            String nestedParams,
                                                            String returnType,
                                                            String nestedReturned) throws ClassNotFoundException {
        // when(mockObject.mockedMethod(param1, param2, ...)).thenReturn(returned);
        String mockedMethodWithParams = MethodInvocationUtil.getMethodWithParamsFromInvocationFQN(invocation);

        CtStatementList whenThenStatements = factory.createStatementList();

        String objectFileUUID = UUID.randomUUID().toString();

        if (nestedParams.contains("<object-array>")) {
            if (nestedParams.length() < 1000) {
                whenThenStatements = MockGeneratorUtil.createParamVariableAndParse(paramTypes, nestedParams);
            } else {
                whenThenStatements = MockGeneratorUtil.createParamFileAndParse(paramTypes, nestedParams,
                        mockedMethodWithParams + objectFileUUID);
            }
            nestedParams = MockGeneratorUtil.getNestedParamsArgumentForInvocations(paramTypes);
        }

        CtStatementList returnStatements;
        if (nestedReturned != null & !MockGeneratorUtil.arePrimitiveOrString(List.of(returnType))) {
            if (nestedReturned.length() < 1000) {
                returnStatements = MockGeneratorUtil.createReturnedVariableAndParse(returnType, nestedReturned);
            } else {
                returnStatements = MockGeneratorUtil.createReturnedFileAndParse(returnType, nestedReturned,
                        mockedMethodWithParams + objectFileUUID);
            }
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
                        "mock" + mockVariableSimpleName,
                        MethodInvocationUtil.getMethodName(mockedMethodWithParams),
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
                                                       String mockVariableName,
                                                       List<String> paramTypes,
                                                       String nestedParams,
                                                       SerializedObject nestedSerializedObject) throws ClassNotFoundException {
        if (nestedParams.contains("<object-array>")) {
            nestedParams = MockGeneratorUtil.getNestedParamsArgumentForInvocations(paramTypes);
        }
        // Mockito.verify(mockedObject, Mockito.atLeastOnce())
        CtExecutableReference<?> executableReferenceForMockitoVerify = factory.createExecutableReference();
        executableReferenceForMockitoVerify.setSimpleName("verify");
        executableReferenceForMockitoVerify.setStatic(true);
        executableReferenceForMockitoVerify.setDeclaringType(factory.createCtTypeReference(Class.forName(MOCKITO_REFERENCE)));
        CtExpression<?> mockedFieldExpression = factory.createCodeSnippetExpression(mockVariableName);
        CtInvocation mockitoVerifyInvocation = factory.createInvocation();
        mockitoVerifyInvocation.setExecutable(executableReferenceForMockitoVerify);
        mockitoVerifyInvocation.setTarget(factory.createTypeAccess(factory.createCtTypeReference(Class.forName(MOCKITO_REFERENCE))));
        if (nestedSerializedObject != null) {
            CtExpression<?> mockitoAtLeastOnceExpression = factory.createCodeSnippetExpression("Mockito.atLeastOnce()");
            mockitoVerifyInvocation.setArguments(Arrays.asList(mockedFieldExpression, mockitoAtLeastOnceExpression));
        } else {
            CtExpression<?> mockitoNeverExpression = factory.createCodeSnippetExpression("Mockito.never()");
            mockitoVerifyInvocation.setArguments(Arrays.asList(mockedFieldExpression, mockitoNeverExpression));
        }

        // Mockito.verify(mockedObject, Mockito.atLeastOnce()).mockedMethod(param1, param2, ...)
        String mockedMethodWithParams = MethodInvocationUtil.getMethodWithParamsFromInvocationFQN(invocation);
        CtExecutableReference<?> executableReferenceForMockedMethod = factory.createExecutableReference();
        executableReferenceForMockedMethod.setSimpleName(MethodInvocationUtil.getMethodName(mockedMethodWithParams));
        CtInvocation mockedMethodInvocation = factory.createInvocation();
        mockedMethodInvocation.setExecutable(executableReferenceForMockedMethod);
        mockedMethodInvocation.setTarget(mockitoVerifyInvocation);
        if (nestedSerializedObject != null) {
            mockedMethodInvocation.setArguments(List.of(factory.createCodeSnippetExpression(nestedParams)));
        } else {
            List<CtExecutableReference<?>> paramExecutables =
                    MockGeneratorUtil.convertParamsToMockitoArgumentMatchers(paramTypes);
            mockedMethodInvocation.setArguments(List.of(factory.createCodeSnippetExpression(paramExecutables
                    .toString().substring(1, paramExecutables.toString().lastIndexOf(']')))));
        }
        return mockedMethodInvocation;
    }

    private CtMethod<?> generateTestWithMock(CtMethod<?> testWithMock,
                                             String mockedMethodName,
                                             CtInvocation<?> mockitoVerifyInvocation,
                                             CtStatementList mockitoWhenThenInvocation,
                                             String uuid, String targetFieldName) throws ClassNotFoundException {
        // when(mockedObject.mockedMethod(<params>)).thenReturn(<returned-object>);
        // assertEquals(<returned-object>, receivingObject.targetMethod(<params>));
        // verify(mockedObject).mockedMethod
        testWithMock.setSimpleName("testWithMock" + mockedMethodName.substring(0, 1).toUpperCase() +
                mockedMethodName.substring(1) +
                (uuid.isEmpty() ? ++counter : "_" + uuid.replace("-", "")) +
                (!targetFieldName.isEmpty() ? "_" + targetFieldName : ""));
        if (mockitoWhenThenInvocation != null) {
            testWithMock.getBody().getLastStatement().insertBefore(mockitoWhenThenInvocation);
        }
        testWithMock.getBody().insertEnd(mockitoVerifyInvocation);
        // Add classloader variable if reading xml from file(s)
        CtStatement classLoaderVar = null;
        List<CtStatement> statements = testWithMock.getBody().getStatements();
        for (CtStatement statement : statements) {
            if (statement.toString().contains("classLoader.getResource")) {
                classLoaderVar = MockGeneratorUtil.delegateClassLoaderVariableCreation();
                break;
            }
        }
        if (classLoaderVar != null) {
            testWithMock.getBody().insertBegin(classLoaderVar);
        }
        CtAnnotation<?> disabledAnnotation = factory.createAnnotation(
                factory.createCtTypeReference(Class.forName(JUNIT_JUPITER_DISABLED_REFERENCE)));
        testWithMock.removeAnnotation(disabledAnnotation);
        return testWithMock;
    }
}
