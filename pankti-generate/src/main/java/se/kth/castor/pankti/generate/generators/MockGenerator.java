package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.data.InstrumentedMethod;
import se.kth.castor.pankti.generate.data.NestedInvocation;
import se.kth.castor.pankti.generate.data.SerializedObject;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.*;
import java.util.stream.Collectors;

public class MockGenerator {
    static Factory factory;

    private static final String JUNIT_EXTEND_WITH_REFERENCE = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String JUNIT_BEFORE_EACH_REFERENCE = "org.junit.jupiter.api.BeforeEach";
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
     * Generates @Mock, @InjectMocks fields, as well as @Test methods that use these mocks
     *
     * @param generatedTestClass is the test class being generated
     * @param method             is the method to generate tests for
     * @return a list of fields that have been generated
     * @throws ClassNotFoundException
     */
    public List<CtMethod<?>> generateMockInfrastructure(CtMethod<?> baseMethod,
                                                        SerializedObject serializedObject,
                                                        CtClass<?> generatedTestClass,
                                                        InstrumentedMethod method) throws ClassNotFoundException {
        String nestedMethodMap = method.getNestedMethodMap();
        List<String> sanitizedInvocations = MockGeneratorUtil.getListOfInvocationsFromNestedMethodMap(nestedMethodMap);
        List<String> nestedReturnTypes = MockGeneratorUtil.getReturnTypeFromInvocationMap(nestedMethodMap);
        List<String> invocationTargetTypes = MockGeneratorUtil.getNestedInvocationTargetTypes(nestedMethodMap);
        assert (sanitizedInvocations.size() == nestedReturnTypes.size() &
                nestedReturnTypes.size() == invocationTargetTypes.size());

        List<NestedInvocation> nestedInvocations = new ArrayList<>();
        for (int i = 0; i < sanitizedInvocations.size(); i++) {
            nestedInvocations.add(new NestedInvocation(
                    sanitizedInvocations.get(i),
                    nestedReturnTypes.get(i), invocationTargetTypes.get(i)));
        }

        // @InjectMocks ReceivingObject receivingObject = new ReceivingObject();
        CtField<?> mockInjectedField = generateFieldToInjectMocksInto(generatedTestClass, method);

        // Clean up base method, remove non-required statements
        baseMethod = MockGeneratorUtil.cleanUpBaseMethodCloneForMocking(baseMethod, mockInjectedField);

        List<CtMethod<?>> generatedTestsWithMocks = new ArrayList<>();

        for (NestedInvocation nestedInvocation : nestedInvocations) {
            CtField<?> mockField = generateAnnotatedMockField(generatedTestClass, nestedInvocation.getInvocation());
            // Use the generated mock field in a generated test that uses mocks
            for (SerializedObject nested : serializedObject.getNestedSerializedObjects()) {
                if (nested.getInvocationFQN().equals(nestedInvocation.getInvocation())) {
                    System.out.println("Generating test with mock for " + nestedInvocation);

                    // Get serialized nested params
                    String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(nestedInvocation.getInvocation());
                    List<String> paramTypes = MockGeneratorUtil.getParametersOfMockedMethod(mockedMethodWithParams);
                    String nestedParams;
                    if (paramTypes.size() == 1 & paramTypes.get(0).isEmpty()) {
                        nestedParams = "";
                    } else if (MockGeneratorUtil.arePrimitive(paramTypes)) {
                        nestedParams = MockGeneratorUtil.extractParamsOfNestedInvocation(paramTypes, nested);
                    } else {
                        nestedParams = nested.getParamObjects();
                    }

                    if (nestedInvocation.getInvocationTargetType().equals("PARAMETER")) {
                        baseMethod = MockGeneratorUtil.updateAssertionForInvocationOnParameters(method.getParamList(),
                                baseMethod, mockField.getType().getQualifiedName(), mockField.getSimpleName());
                    }

                    // For non-void methods, get serialized nested returned value and generate stub
                    CtStatementList mockitoWhenThenInvocation = null;
                    if (!nestedInvocation.getInvocationReturnType().equals("void")) {
                        String nestedReturned = MockGeneratorUtil.extractReturnedValueOfNestedInvocation(
                                nested, nestedInvocation.getInvocationReturnType());
                        mockitoWhenThenInvocation = createMockitoWhenThenInvocation(
                                nestedInvocation.getInvocation(), mockField, paramTypes, nestedParams, nestedInvocation.getInvocationReturnType(), nestedReturned);
                    }

                    CtInvocation mockitoVerifyInvocation = createMockitoVerifyInvocation(nestedInvocation.getInvocation(), mockField, paramTypes, nestedParams);
                    String mockedMethodNameAndParams = MockGeneratorUtil.getMockedMethodWithParams(nestedInvocation.getInvocation());
                    String mockedMethodName = MockGeneratorUtil.getMockedMethodName(mockedMethodNameAndParams);
                    CtMethod<?> testWithMock = generateTestWithMock(baseMethod.clone(), mockedMethodName, mockitoVerifyInvocation, mockitoWhenThenInvocation, nested.getUUID());
                    generatedTestsWithMocks.add(testWithMock);

                    // @BeforeEach method to set up mock injection for non-default constructors
                    if (!method.hasNoParamConstructor()) {
                        String setupMethodName = "setUpMockInjection_" + mockInjectedField.getSimpleName();
                        if (generatedTestClass.getMethodsByName(setupMethodName).isEmpty()) {
                            setUpMockInjectionMethod(mockInjectedField, method.getSmallestParamConstructor(),
                                    generatedTestClass, setupMethodName);
                        }
                    }
                }
            }
        }

        System.out.println("Generated " + generatedTestsWithMocks.size() + " test(s) with mocks");
        return generatedTestsWithMocks;
    }

    private CtField<?> generateFieldToInjectMocksInto(CtClass<?> generatedClass,
                                                      InstrumentedMethod method) throws ClassNotFoundException {

        CtField<?> injectMockField;
        if (method.hasNoParamConstructor()) {
            injectMockField = factory.createCtField(
                    method.getParentSimpleName().toLowerCase(),
                    MockGeneratorUtil.findTypeFromModel(method.getParentFQN()).get(0),
                    String.format("new %s()", method.getParentSimpleName())
            );
        } else {
            injectMockField = factory.createField();
            injectMockField.setSimpleName(method.getParentSimpleName().toLowerCase());
            injectMockField.setType(MockGeneratorUtil.findTypeFromModel(method.getParentFQN()).get(0));
        }
        injectMockField.addAnnotation(generateMockitoAnnotation(MOCKITO_INJECT_MOCKS_REFERENCE));
        addFieldToGeneratedClass(generatedClass, injectMockField);
        return injectMockField;
    }

    private CtField<?> generateAnnotatedMockField(CtClass<?> generatedTestClass,
                                                  String invocation) throws ClassNotFoundException {
        String declaringTypeToMock = MockGeneratorUtil.getDeclaringTypeToMock(invocation);

        CtTypeReference mockFieldType = MockGeneratorUtil.findTypeFromModel(declaringTypeToMock).get(0);
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
        if (nestedReturned != null & !MockGeneratorUtil.arePrimitive(List.of(returnType))) {
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
        return testWithMock;
    }

    public void setUpMockInjectionMethod(CtField<?> mockInjectedField,
                                         String constructorParams,
                                         CtClass<?> generatedTestClass,
                                         String setupMethodName) throws ClassNotFoundException {
        System.out.println("Generating setup method " + setupMethodName + " in " + generatedTestClass.getQualifiedName());
        CtMethod<?> mockInjectionSetupMethod = factory.createMethod();
        mockInjectionSetupMethod.addAnnotation(generateMockitoAnnotation(JUNIT_BEFORE_EACH_REFERENCE));
        mockInjectionSetupMethod.setSimpleName(setupMethodName);
        mockInjectionSetupMethod.setVisibility(ModifierKind.PUBLIC);
        mockInjectionSetupMethod.setType(factory.createCtTypeReference(void.class));

        // Generate @Mock field for each constructor param
        List<String> constructorParamList = List.of(constructorParams.split(","));

        // Do not regenerate already existing mock field
        List<String> generatedClassMockFields = generatedTestClass.getFields().stream()
                .filter(field -> field.getAnnotations().toString().contains("@Mock"))
                .map(field -> field.getType().getQualifiedName())
                .collect(Collectors.toList());

        System.out.println("constructor params: " + constructorParamList);
        List<String> constructorArgs = new ArrayList<>();
        for (String param : constructorParamList) {
            // create mock fields for constructor params
            if (!generatedClassMockFields.contains(param)) {
                CtField<?> mockField = factory.createField();
                mockField.setSimpleName(param.replaceAll("(.+\\.)(\\w+$)", "mock$2"));
                try {
                    mockField.setType(factory.createCtTypeReference(Class.forName(param)));
                } catch (ClassNotFoundException e) {
                    mockField.setType(MockGeneratorUtil.findTypeFromModel(param).get(0));
                }
                mockField.setModifiers(Set.of(ModifierKind.PRIVATE));
                mockField.addAnnotation(generateMockitoAnnotation(MOCKITO_MOCK_REFERENCE));
                addFieldToGeneratedClass(generatedTestClass, mockField);
            }
            constructorArgs.add(param.replaceAll("(.+\\.)(\\w+$)", "mock$2"));
        }

        // injectMockField = new InjectMockField(mockParam1, mockParam2);
        CtStatement injectMockStatement = factory.createCodeSnippetStatement(
                String.format("%s = new %s(%s)",
                        mockInjectedField.getSimpleName(), mockInjectedField.getType().getSimpleName(),
                        constructorArgs.toString().replace("[", "").replaceAll("]", ""))
        );

        mockInjectionSetupMethod.setBody(injectMockStatement);
        generatedTestClass.addMethod(mockInjectionSetupMethod);
    }
}
