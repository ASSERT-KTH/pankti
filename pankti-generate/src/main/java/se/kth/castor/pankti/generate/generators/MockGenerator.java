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
import java.util.stream.Collectors;

public class MockGenerator {
    static Factory factory;
    static int counter;
    private static final String JUNIT_EXTEND_WITH_REFERENCE = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String JUNIT_JUPITER_TEST_REFERENCE = "org.junit.jupiter.api.Test";
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
                                                      String mockVariableName,
                                                      List<String> paramTypes,
                                                      String nestedParams) throws ClassNotFoundException {
        // For non-void methods, get serialized nested returned value and generate stub
        CtStatementList mockitoWhenThenInvocation = null;
        if (!nestedInvocation.getInvocationReturnType().equals("void")) {
            String nestedReturned = MockGeneratorUtil.extractReturnedValueOfNestedInvocation(
                    nestedSerializedObject, nestedInvocation.getInvocationReturnType());
            mockitoWhenThenInvocation = createMockitoWhenThenInvocation(nestedInvocation.getInvocation(),
                    mockVariableName, paramTypes, nestedParams,
                    nestedInvocation.getInvocationReturnType(), nestedReturned);
        }
        return mockitoWhenThenInvocation;
    }

    /**
     * Generate one OO and one PO method per invocation (serializedObject)
     *
     * @param baseMethod
     * @param serializedObjectMUT
     * @param generatedTestClass
     * @param targetMUT
     * @return
     * @throws ClassNotFoundException
     */
    public CtMethod<?> generateTestByCategory(String testCategory,
                                              CtMethod<?> baseMethod,
                                              SerializedObject serializedObjectMUT,
                                              CtClass<?> generatedTestClass,
                                              InstrumentedMethod targetMUT) throws ClassNotFoundException {
        System.out.println("Generating " + testCategory + " test for " + targetMUT.getMethodName());
        CtMethod<?> generatedTest;
        CtBlock<?> testOOBody = factory.createBlock();
        String receivingObjectTypeMUT = serializedObjectMUT.getObjectType(serializedObjectMUT.getReceivingObject());
        String receivingObjectTypeMUTSimple = MethodInvocationUtil.getDeclaringTypeSimpleNameFromFQN(receivingObjectTypeMUT);

        List<CtStatementList> stubStatements = new ArrayList<>();
        List<CtStatement> verificationStatementsPO = new ArrayList<>();

        // Get all nested serialized objects corresponding to this invocation
        List<SerializedObject> nestedSerializedObjects = serializedObjectMUT.getNestedSerializedObjects();

        List<NestedInvocation> nestedInvocations = targetMUT.getNestedInvocations();

        List<String> mockParameterFQNs = new ArrayList<>();
        List<String> mockParameterNames = new ArrayList<>();
        Set<String> mockVariableNames = new LinkedHashSet<>();

        // Stub all mockable methods
        for (SerializedObject nestedSerializedObject : nestedSerializedObjects) {
            for (NestedInvocation nestedInvocation : nestedInvocations) {
                if (nestedSerializedObject.getInvocationFQN().equals(nestedInvocation.getInvocation())) {
                    System.out.println("- Processing invocation " + nestedInvocation.getInvocation());
                    String mockVariableTypeFQN = MethodInvocationUtil.getDeclaringTypeFromInvocationFQN(nestedInvocation.getInvocation());
                    String mockVariableTypeSimple = MethodInvocationUtil.getDeclaringTypeSimpleNameFromFQN(mockVariableTypeFQN);
                    String mockVariableName = "mock" + mockVariableTypeSimple.replace(".", "");
                    mockVariableNames.add(mockVariableName);
                    List<String> paramTypes = getParamTypes(nestedInvocation);
                    String nestedParams = prepareNestedParams(paramTypes, nestedSerializedObject);
                    String invocationTargetType = nestedInvocation.getInvocationTargetType();

                    // if invocationTargetType is field
                    if (invocationTargetType.equals("FIELD")) {
                        // generate helper methods to inject mocks as fields
                        String helperMethodName;
                        Map<String, String> fieldVisibilityMap = nestedInvocation.getInvocationFieldsVisibilityMap();
                        for (Map.Entry<String, String> entry : fieldVisibilityMap.entrySet()) {
                            boolean targetFieldIsPrivate = nestedInvocation.isTargetFieldPrivate(entry);
                            String targetFieldName = nestedInvocation.getTargetFieldName(entry);
                            if (nestedInvocation.isTargetFieldPrivate(entry)) {
                                helperMethodName = String.format("insertPrivateMockField_%s_In%s",
                                        targetFieldName, receivingObjectTypeMUTSimple);
                            } else {
                                helperMethodName = String.format("insertMockField_%s_In%s",
                                        targetFieldName, receivingObjectTypeMUTSimple);
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
                                                receivingObjectTypeMUT);
                                generatedTestClass.addMethod(helperMethodForMockFieldInjection);
                            }

                            CtStatement addMockFieldToTest = factory.createCodeSnippetStatement(String.format(
                                    "%s %s = %s(receivingObject)",
                                    mockVariableTypeSimple,
                                    mockVariableName,
                                    helperMethodName
                            ));
                            if (testOOBody.getStatements().stream()
                                    .noneMatch(s -> s.toString().equals(addMockFieldToTest.toString()))) {
                                testOOBody.addStatement(addMockFieldToTest);
                            }
                        }
                    } else {
                        // if invocationTargetType is parameter, mock parameter object
                        CtStatement mockParameterStatement = MockGeneratorUtil.generateLocalVariableForMockParameter(
                                mockVariableName,
                                mockVariableTypeSimple);
                        mockParameterFQNs.add(mockVariableTypeFQN);
                        mockParameterNames.add(mockVariableName);
                        if (testOOBody.getStatements().stream()
                                .noneMatch(s -> s.toString().equals(mockParameterStatement.toString()))) {
                            testOOBody.addStatement(mockParameterStatement);
                        }
                    }
                    if (!nestedInvocation.getInvocationReturnType().equals("void")) {
                        CtStatementList stubStatement = prepareWhenThenInvocation(nestedInvocation,
                                nestedSerializedObject, mockVariableName, paramTypes, nestedParams);
                        if (stubStatements.stream().noneMatch(s ->
                                s.toString().equals(stubStatement.toString()))) {
                            stubStatements.add(stubStatement);
                        }
                    }

                    // Generate verification statements for PO and CO
                    if (!testCategory.equals("OO")) {
                        CtStatement verificationStatement = createMockitoVerifyInvocation(nestedInvocation,
                                mockVariableName, paramTypes, nestedParams, nestedSerializedObject);
                        if (verificationStatementsPO.stream().noneMatch(v ->
                                v.toString().equals(verificationStatement.toString()))) {
                            verificationStatementsPO.add(verificationStatement);
                        }
                    }
                }
            }
        }

        generatedTest = MockGeneratorUtil.cleanUpBaseMethodCloneForMocking(baseMethod,
                MockGeneratorUtil.arePrimitiveOrString(List.of(targetMUT.getReturnType())),
                targetMUT.getReturnType().equals("void"),
                false);

        generatedTest.setSimpleName(String.format("test_%s_%s_%s", targetMUT.getMethodName(),
                testCategory, serializedObjectMUT.getUUID().replace("-", "")));

        CtStatement mutCallStatement = generatedTest.getBody().getLastStatement();
        generatedTest.getBody().removeStatement(mutCallStatement);

        for (CtStatement mockVariableStatement : testOOBody.getStatements()) {
            generatedTest.getBody().addStatement(mockVariableStatement);
        }
        for (CtStatementList statements : stubStatements) {
            for (CtStatement s : statements) {
                generatedTest.getBody().addStatement(s);
            }
        }

        Set<String> intersectionBetweenParamListAndMockFQNs = targetMUT.getParamList().stream()
                .distinct().filter(mockParameterFQNs::contains)
                .collect(Collectors.toSet());
        if (intersectionBetweenParamListAndMockFQNs.size() == 0 & mockParameterFQNs.size() > 0) {
            // TODO: This is very makeshift
            for (int i = 0; i < targetMUT.getParamList().size(); i++) {
                mutCallStatement = MockGeneratorUtil.updateAssertionForInvocationOnParameters(mutCallStatement,
                        targetMUT.getParamList(), targetMUT.getParamList().get(i), mockParameterNames.get(i));
            }
        } else {
            for (int i = 0; i < mockParameterFQNs.size(); i++) {
                mutCallStatement = MockGeneratorUtil.updateAssertionForInvocationOnParameters(mutCallStatement,
                        targetMUT.getParamList(), mockParameterFQNs.get(i), mockParameterNames.get(i));
            }
        }

        // remove assertion on MUT output for PO and CO tests
        if (!testCategory.equals("OO")) {
            mutCallStatement = factory.createCodeSnippetStatement(mutCallStatement.toString()
                    .replaceAll(".+\\(.+,\\s(receivingObject.+\\))\\)",
                            "$1"));
        }
        generatedTest.getBody().addStatement(mutCallStatement);

        // For PO tests, add verification statements with concrete parameters
        if (testCategory.equals("PO")) {
            for (CtStatement statement : verificationStatementsPO) {
                generatedTest.getBody().addStatement(statement);
            }
        }

        // Add classloader variable if reading xml from file(s)
        CtStatement classLoaderVar = null;
        List<CtStatement> statements = generatedTest.getBody().getStatements();
        for (CtStatement statement : statements) {
            if (statement.toString().contains("classLoader.getResource")) {
                classLoaderVar = MockGeneratorUtil.delegateClassLoaderVariableCreation();
                break;
            }
        }
        if (classLoaderVar != null) {
            generatedTest.getBody().insertBegin(classLoaderVar);
        }

        CtAnnotation<?> disabledAnnotation = factory.createAnnotation(
                factory.createCtTypeReference(Class.forName(JUNIT_JUPITER_DISABLED_REFERENCE)));
        generatedTest.removeAnnotation(disabledAnnotation);
        return generatedTest;
    }

    private CtStatementList createMockitoWhenThenInvocation(String invocation,
                                                            String mockVariableName,
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
                        mockVariableName,
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

    private CtInvocation createMockitoVerifyInvocation(NestedInvocation nestedInvocation,
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
        CtExpression<?> mockitoAtLeastOnceExpression = factory.createCodeSnippetExpression("Mockito.atLeastOnce()");
        mockitoVerifyInvocation.setArguments(Arrays.asList(mockedFieldExpression, mockitoAtLeastOnceExpression));

        // Mockito.verify(mockedObject, Mockito.atLeastOnce()).mockedMethod(param1, param2, ...)
        String mockedMethodWithParams = MethodInvocationUtil.getMethodWithParamsFromInvocationFQN(nestedInvocation.getInvocation());
        CtExecutableReference<?> executableReferenceForMockedMethod = factory.createExecutableReference();
        executableReferenceForMockedMethod.setSimpleName(MethodInvocationUtil.getMethodName(mockedMethodWithParams));
        CtInvocation mockedMethodInvocation = factory.createInvocation();
        mockedMethodInvocation.setExecutable(executableReferenceForMockedMethod);
        mockedMethodInvocation.setTarget(mockitoVerifyInvocation);
        mockedMethodInvocation.setArguments(List.of(factory.createCodeSnippetExpression(nestedParams)));
        return mockedMethodInvocation;
    }
}
