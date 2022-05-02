package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.CSVFileParser;
import se.kth.castor.pankti.generate.data.InstrumentedMethod;
import se.kth.castor.pankti.generate.parsers.ObjectXMLParser;
import se.kth.castor.pankti.generate.data.SerializedObject;
import se.kth.castor.pankti.generate.util.MockGeneratorUtil;
import se.kth.castor.pankti.generate.util.TestGeneratorUtil;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class TestGenerator {
    private static Factory factory;
    private static final String XSTREAM_REFERENCE = "com.thoughtworks.xstream.XStream";
    private static final String XSTREAM_DRIVER_REFERENCE = "com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver";
    private static final String XSTREAM_CONSTRUCTOR = "new XStream()";
    private static final String XSTREAM_VARIABLE = "xStream";
    private static final String JUNIT_JUPITER_TEST_REFERENCE = "org.junit.jupiter.api.Test";
    private static final String JUNIT_JUPITER_DISABLED_REFERENCE = "org.junit.jupiter.api.Disabled";
    private static final String JUNIT_BEFORE_REFERENCE = "org.junit.Before";
    private static final String JUNIT_ASSERT_REFERENCE = "org.junit.jupiter.api.Assertions";
    private static final String JAVA_UTIL_ARRAYS_REFERENCE = "java.util.Arrays";
    private static final String JAVA_UTIL_SCANNER_REFERENCE = "java.util.Scanner";
    private static final String JAVA_IO_FILE_REFERENCE = "java.io.File";
    private static final String serializedObjectSourcedFromString = "String";
    private static final String serializedObjectSourcedFromFile = "File";

    private static final String TEST_CLASS_PREFIX = "Test";
    private static final String TEST_CLASS_POSTFIX = "PanktiGen";
    private static int numberOfTestCasesWithMocksGenerated;
    private final String testFormat;
    private final boolean generateMocks;
    private static CtAnnotation<?> testAnnotation;
    private static CtAnnotation<?> disabledAnnotation;
    Set<CtClass<?>> generatedTestClasses = new LinkedHashSet<>();

    private final TestGeneratorUtil testGenUtil = new TestGeneratorUtil();

    public TestGenerator(String testFormat, Launcher launcher, boolean generateMocks) {
        this.generateMocks = generateMocks;
        this.testFormat = testFormat;
        TestGeneratorUtil.launcher = launcher;
        if (!testFormat.equals("xml")) {
            TestGeneratorUtil.testFormat = testFormat;
        }
    }

    public static void generateTestAndDisabledAnnotations() throws ClassNotFoundException {
        testAnnotation = factory.createAnnotation(factory.createCtTypeReference(Class.forName(JUNIT_JUPITER_TEST_REFERENCE)));
        disabledAnnotation = factory.createAnnotation(factory.createCtTypeReference(Class.forName(JUNIT_JUPITER_DISABLED_REFERENCE)));
    }

    public String getGeneratedClassName(CtPackage ctPackage, String className) {
        return String.format("%s.%s%s%s", ctPackage, TEST_CLASS_PREFIX, className, TEST_CLASS_POSTFIX);
    }

    /**
     * Generates a test class within the specified package
     *
     * @param ctPackage for the generated test class
     * @param className the name of the class that defines the target method
     * @return a new test class for the target method
     */
    public CtClass<?> generateTestClass(CtPackage ctPackage, String className) {
        CtClass<?> generatedClass = factory.createClass(ctPackage, TEST_CLASS_PREFIX + className + TEST_CLASS_POSTFIX);
        generatedClass.addModifier(ModifierKind.PUBLIC);
        return generatedClass;
    }

    /**
     * Adds all imports to a class
     *
     * @param generatedClass the test class to add imports to
     */
    public void addImportsToGeneratedClass(CtClass<?> generatedClass) {
        generatedClass.getFactory().createUnresolvedImport(XSTREAM_REFERENCE, false);
        if (this.testFormat.equals("json")) {
            generatedClass.getFactory().createUnresolvedImport(XSTREAM_DRIVER_REFERENCE, false);
        }
        generatedClass.getFactory().createUnresolvedImport(JUNIT_JUPITER_TEST_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JUNIT_BEFORE_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_UTIL_ARRAYS_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_UTIL_SCANNER_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_IO_FILE_REFERENCE, false);
    }

    /**
     * Adds and sets up XStream for deserialization
     *
     * @return the generated xStream field
     */
    public CtField<?> addXStreamFieldToGeneratedClass() throws ClassNotFoundException {
        CtField<?> xStreamField = null;

        xStreamField = factory.createCtField(
                XSTREAM_VARIABLE,
                factory.createCtTypeReference(Class.forName(XSTREAM_REFERENCE)),
                XSTREAM_CONSTRUCTOR
        );

        if (this.testFormat.equals("json")) {
            xStreamField.setDefaultExpression(factory.createConstructorCall(
                    factory.createCtTypeReference(Class.forName(XSTREAM_REFERENCE)),
                    factory.createConstructorCall(factory.createCtTypeReference(Class.forName(XSTREAM_DRIVER_REFERENCE)))
            ));
        }

        xStreamField.addModifier(ModifierKind.STATIC);
        return xStreamField;
    }

    // overloaded method deserializeObject abstracts away xStream call in test methods
    public List<CtMethod<?>> addDeserializationMethodsToTestClass() {
        List<CtMethod<?>> deserializationMethods = new ArrayList<>();

        CtMethod<?> deserializeObjectFromString = testGenUtil.generateDeserializationMethod(factory, serializedObjectSourcedFromString);
        CtParameter<?> parameterWithSerializedString = factory.createParameter();
        parameterWithSerializedString.setType(factory.createCtTypeReference(String.class));
        parameterWithSerializedString.setSimpleName("serializedObjectString");
        deserializeObjectFromString.setParameters(Collections.singletonList(parameterWithSerializedString));

        CtMethod<?> deserializeObjectFromFile = testGenUtil.generateDeserializationMethod(factory, serializedObjectSourcedFromFile);
        deserializeObjectFromFile.addThrownType(factory.createCtTypeReference(Exception.class));
        CtParameter<?> parameterWithSerializedFilePath = factory.createParameter();
        parameterWithSerializedFilePath.setType(factory.createCtTypeReference(String.class));
        parameterWithSerializedFilePath.setSimpleName("serializedObjectFilePath");
        deserializeObjectFromFile.setParameters(Collections.singletonList(parameterWithSerializedFilePath));
        CtBlock<?> methodBody = deserializeObjectFromFile.getBody();
        methodBody.insertBegin(testGenUtil.addAndReadFromScannerInDeserializationMethod(factory));
        deserializeObjectFromFile.setBody(methodBody);

        deserializationMethods.add(deserializeObjectFromString);
        deserializationMethods.add(deserializeObjectFromFile);
        return deserializationMethods;
    }

    /**
     * Generates assertions in a generated test method
     *
     * @param method           target for test generation
     * @param serializedObject a single object profile
     * @return a list of statements for profile deserialization and the assertion itself
     */
    @SuppressWarnings("unchecked")
    public List<CtStatement> generateAssertionInTestMethod(CtMethod<?> method, SerializedObject serializedObject) throws ClassNotFoundException {
        List<CtStatement> assertionStatements = new ArrayList<>();
        CtExpression<?> assertExpectedObject;
        if (method.getType().getSimpleName().equals("void")) {
            // for void methods, assertion on post receiving object
            assertExpectedObject = factory.createCodeSnippetExpression(String.format("%s.toXML(receivingObjectPost)", XSTREAM_VARIABLE));
        } else if (method.getType().isPrimitive() || method.getType().getQualifiedName().equals("java.lang.String")) {
            String value = serializedObject.getReturnedObject().replaceAll("</?\\w+>", "");
            if (method.getType().getQualifiedName().equals("java.lang.String"))
                value = "\"" + value + "\"";
            assertExpectedObject = factory.createCodeSnippetExpression(
                    method.getType().getSimpleName().equals("char") ?
                            "'" + value + "'" :
                            value);
        } else if (testGenUtil.returnedObjectIsNull(serializedObject.getReturnedObject())) {
            assertExpectedObject = factory.createLiteral(null);
        } else {
            assertExpectedObject = factory.createCodeSnippetExpression("expectedObject");
        }

        List<CtParameter<?>> parameters = method.getParameters();
        StringBuilder arguments = new StringBuilder();

        boolean hasOnlyPrimitiveParams = testGenUtil.allMethodParametersArePrimitive(method);

        // If parameters are primitives
        List<String> primitiveParams = new ArrayList<>();
        if (parameters.size() > 0 & hasOnlyPrimitiveParams) {
            primitiveParams = Arrays.asList(
                    serializedObject.getParamObjects()
                            .replaceAll("</?object-array>", "")
                            .trim()
                            .split("\\n"));
        }

        for (int i = 1; i <= parameters.size(); i++) {
            // TODO: handling of char parameters needs improvement
            if (hasOnlyPrimitiveParams) {
                arguments.append(primitiveParams.get(i - 1)
                        .replaceAll("<char>(.{1})<\\/char>", "<char>'$1'</char>")
                        .replaceAll("<long>(.+)<\\/long>", "<long>$1L</long>")
                        .replaceAll("<float>(.+)<\\/float>", "<float>$1F</float>")
                        .replaceAll("<\\w+>(.+)<\\/\\w+>", "$1")
                        .replaceAll("\\s", ""));
            } else {
                arguments.append("paramObject").append(i);
            }
            if (i != parameters.size()) {
                arguments.append(", ");
            }
        }

        CtExpression<?> assertActualObject;

        String assertionStatement = String.format("receivingObject.%s(%s)",
                method.getSimpleName(),
                arguments);
        if (method.getType().getSimpleName().equals("void")) {
            CtStatement methodInvocation = factory.createCodeSnippetStatement(assertionStatement);
            assertionStatements.add(methodInvocation);
            assertActualObject = factory.createCodeSnippetExpression(String.format("%s.toXML(receivingObject)", XSTREAM_VARIABLE));
        } else {
            assertActualObject = factory.createCodeSnippetExpression(assertionStatement);
        }

        // Reflection for private methods
        if (method.getVisibility().equals(ModifierKind.PRIVATE)) {
            assertActualObject = factory.createCodeSnippetExpression(
                    String.format("%s%s.invoke(%s, %s)",
                            method.getType().isArray() ? "(" + method.getType() + ") " : "",
                            method.getSimpleName(),
                            "receivingObject",
                            arguments));
        }

        // Generate JUnit assert invocation
        CtExecutableReference<?> executableReferenceForAssertion = factory.createExecutableReference();
        executableReferenceForAssertion.setStatic(true);
        executableReferenceForAssertion.setDeclaringType(factory.createCtTypeReference(Class.forName(JUNIT_ASSERT_REFERENCE)));
        CtInvocation assertInvocation = factory.createInvocation();

        if (method.getType().isArray()) {
            // if method returns an array, Assert.assertTrue(Arrays.equals(expected, actual))
            executableReferenceForAssertion.setSimpleName("assertTrue");
            assertInvocation.setExecutable(executableReferenceForAssertion);
            assertInvocation.setTarget(factory.createTypeAccess(factory.createCtTypeReference(Class.forName(JUNIT_ASSERT_REFERENCE))));
            CtInvocation arraysEqualsInvocation = factory.createInvocation();
            CtExecutableReference<?> executableReferenceForArraysEquals = factory.createExecutableReference();
            executableReferenceForArraysEquals.setStatic(true);
            executableReferenceForArraysEquals.setDeclaringType(factory.createCtTypeReference(Class.forName(JAVA_UTIL_ARRAYS_REFERENCE)));
            executableReferenceForArraysEquals.setSimpleName("equals");
            arraysEqualsInvocation.setExecutable(executableReferenceForArraysEquals);
            arraysEqualsInvocation.setTarget(factory.createTypeAccess(factory.createCtTypeReference(Class.forName(JAVA_UTIL_ARRAYS_REFERENCE))));
            arraysEqualsInvocation.setArguments(Arrays.asList(assertExpectedObject, assertActualObject));
            assertInvocation.setArguments(Collections.singletonList(arraysEqualsInvocation));
        } else {
            // Assert.assertEquals(expected, actual)
            executableReferenceForAssertion.setSimpleName("assertEquals");
            assertInvocation.setExecutable(executableReferenceForAssertion);
            assertInvocation.setTarget(factory.createTypeAccess(factory.createCtTypeReference(Class.forName(JUNIT_ASSERT_REFERENCE))));
            assertInvocation.setArguments(Arrays.asList(assertExpectedObject, assertActualObject));
        }
        assertionStatements.add(assertInvocation);
        return assertionStatements;
    }

    public CtStatement parseSerializedObjectFromFilePathOrString(String serializedObjectSource,
                                                                 String serializedObjectType,
                                                                 String serializedObjectVariable,
                                                                 String filePathOrStringVariable) {
        String deserializationMethodName = String.format("deserializeObjectFrom%s", serializedObjectSource);
        if (serializedObjectSource.equals(serializedObjectSourcedFromFile))
            filePathOrStringVariable = "\"" + filePathOrStringVariable + "\"";
        return factory.createCodeSnippetStatement(String.format(
                "%s %s = %s(%s)",
                serializedObjectType,
                serializedObjectVariable,
                deserializationMethodName,
                filePathOrStringVariable
        ));
    }

    public List<CtStatement> addAndParseMethodParams(String paramsObjectStr, CtMethod<?> method) {
        List<CtStatement> paramStatements = new ArrayList<>();
        if (paramsObjectStr.length() <= 100) {
            CtStatement paramsXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "paramsObjectStr", paramsObjectStr);
            paramStatements.add(paramsXMLStringDeclaration);
            CtStatement parseParamObjectsFromString = parseSerializedObjectFromFilePathOrString(
                    serializedObjectSourcedFromString, "Object[]", "paramObjects",
                    "paramsObjectStr");
            paramStatements.add(parseParamObjectsFromString);
        }

        List<CtParameter<?>> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            CtStatement parseParamObject = factory.createCodeSnippetStatement(
                    String.format("%s paramObject%d = (%s) paramObjects[%d]",
                            parameters.get(i).getType().getQualifiedName(),
                            i + 1,
                            testGenUtil.findObjectBoxType(parameters.get(i).getType()),
                            i));
            paramStatements.add(parseParamObject);
        }
        return paramStatements;
    }

    public List<CtStatement> accessPrivateMethod(InstrumentedMethod instrumentedMethod) {
        List<CtStatement> reflectionStatements = new ArrayList<>();
        // Create Class<?> variable
        CtExpression<String> classVariableExpression = factory.createCodeSnippetExpression(
                "Class.forName(\"" + instrumentedMethod.getParentFQN() + "\")"
        );
        CtLocalVariable<?> classVariable = factory.createLocalVariable(
                factory.createCtTypeReference(Class.class),
                "Clazz",
                classVariableExpression);

        // Create param list
        List<String> paramList = instrumentedMethod.getParamList();
        StringBuilder paramString = new StringBuilder();
        if (paramList.size() > 0) {
            paramString = new StringBuilder(", ");
            for (int i = 0; i < paramList.size(); i++) {
                paramString.append(paramList.get(i)).append(".class");
                if (i != paramList.size() - 1)
                    paramString.append(", ");
            }
        }
        // Create Method variable
        CtExpression<String> methodVariableExpression = factory.createCodeSnippetExpression(
                "Clazz.getDeclaredMethod(\"" + instrumentedMethod.getMethodName() + "\"" + paramString + ")"
        );
        CtLocalVariable<?> methodVariable = factory.createLocalVariable(
                factory.createCtTypeReference(Method.class),
                instrumentedMethod.getMethodName(),
                methodVariableExpression);
        CtStatement setAccessibleStatement = factory.createCodeSnippetStatement(instrumentedMethod.getMethodName() + ".setAccessible(true)");

        reflectionStatements.add(classVariable);
        reflectionStatements.add(methodVariable);
        reflectionStatements.add(setAccessibleStatement);
        return reflectionStatements;
    }

    public List<CtStatement> generateStatementsInMethodBody(InstrumentedMethod instrumentedMethod,
                                                            CtMethod<?> method,
                                                            int methodCounter,
                                                            SerializedObject serializedObject,
                                                            String receivingObjectStr,
                                                            String receivingObjectType,
                                                            String returnedObjectStr,
                                                            String returnedObjectType,
                                                            String receivingObjectPostStr,
                                                            String paramsObjectStr) throws ClassNotFoundException {
        List<CtStatement> methodBody = new ArrayList<>();
        String postfix = "";
        if (instrumentedMethod.isOverloaded()) {
            postfix = TestGeneratorUtil.getParamListPostFix(instrumentedMethod.getParamList());
        }
        String methodIdentifier = instrumentedMethod.getFullMethodPath() + postfix + methodCounter;

        if (receivingObjectStr.length() > 100) {
            String type = "receiving";
            String fileName = testGenUtil.createLongObjectStringFile(methodIdentifier, type, receivingObjectStr);
            CtStatement parseReceivingObjectFromFile = parseSerializedObjectFromFilePathOrString(
                    serializedObjectSourcedFromFile, receivingObjectType, "receivingObject",
                    fileName);
            methodBody.add(parseReceivingObjectFromFile);
        } else {
            CtStatement receivingXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "receivingObjectStr", receivingObjectStr);
            CtStatement parseReceivingObjectFromString = parseSerializedObjectFromFilePathOrString(
                    serializedObjectSourcedFromString, receivingObjectType, "receivingObject",
                    "receivingObjectStr");
            methodBody.add(receivingXMLStringDeclaration);
            methodBody.add(parseReceivingObjectFromString);
        }

        if (method.getType().getSimpleName().equals("void")) {
            if (receivingObjectPostStr.length() > 100) {
                String type = "receivingpost";
                String fileName = testGenUtil.createLongObjectStringFile(methodIdentifier, type, receivingObjectPostStr);
                CtStatement parseReceivingPostObjectFromFile = parseSerializedObjectFromFilePathOrString(
                        serializedObjectSourcedFromFile, receivingObjectType, "receivingObjectPost",
                        fileName);
                methodBody.add(parseReceivingPostObjectFromFile);
            } else {
                CtStatement receivingPostXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "receivingPostObjectStr", receivingObjectPostStr);
                CtStatement parseReceivingPostObjectFromString = parseSerializedObjectFromFilePathOrString(
                        serializedObjectSourcedFromString, receivingObjectType, "receivingObjectPost",
                        "receivingPostObjectStr");
                methodBody.add(receivingPostXMLStringDeclaration);
                methodBody.add(parseReceivingPostObjectFromString);
            }
        } else {
            if (returnedObjectStr.length() > 100) {
                String type = "returned";
                String fileName = testGenUtil.createLongObjectStringFile(methodIdentifier, type, returnedObjectStr);
                CtStatement parseReturnedObjectFromFile = parseSerializedObjectFromFilePathOrString(
                        serializedObjectSourcedFromFile, returnedObjectType, "expectedObject",
                        fileName);
                methodBody.add(parseReturnedObjectFromFile);
            } else {
                if (!method.getType().isPrimitive() & !testGenUtil.returnedObjectIsNull(returnedObjectStr)) {
                    CtStatement returnedXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "returnedObjectStr", returnedObjectStr);
                    methodBody.add(returnedXMLStringDeclaration);
                    CtStatement parseReturnedObjectFromString = parseSerializedObjectFromFilePathOrString(
                            serializedObjectSourcedFromString, returnedObjectType, "expectedObject",
                            "returnedObjectStr");
                    methodBody.add(parseReturnedObjectFromString);
                }
            }
        }

        if (!paramsObjectStr.isEmpty()) {
            if (paramsObjectStr.length() > 100) {
                String type = "params";
                String fileName = testGenUtil.createLongObjectStringFile(methodIdentifier, type, paramsObjectStr);
                CtStatement parseParamObjectsFromFile = parseSerializedObjectFromFilePathOrString(
                        serializedObjectSourcedFromFile, "Object[]", "paramObjects",
                        fileName);
                methodBody.add(parseParamObjectsFromFile);
            }
            if (!testGenUtil.allMethodParametersArePrimitive(method)) {
                List<CtStatement> paramStatements = addAndParseMethodParams(paramsObjectStr, method);
                methodBody.addAll(paramStatements);
            }
        }
        if (instrumentedMethod.getVisibility().equals("private")) {
            methodBody.addAll(accessPrivateMethod(instrumentedMethod));
        }

        methodBody.addAll(generateAssertionInTestMethod(method, serializedObject));
        return methodBody;
    }

    public CtMethod<?> generateSetupMethod() throws ClassNotFoundException {
        CtMethod<?> generatedMethod = factory.createMethod();
        generatedMethod.setSimpleName("setUpXStream");
        CtAnnotation<?> beforeAnnotation = factory.createAnnotation(factory.createCtTypeReference(Class.forName(JUNIT_BEFORE_REFERENCE)));
        generatedMethod.addAnnotation(beforeAnnotation);
        generatedMethod.setModifiers(Collections.singleton(ModifierKind.PUBLIC));
        generatedMethod.setType(factory.createCtTypeReference(void.class));
        CtBlock<?> methodBody = factory.createBlock();
        CtStatement registerFileConverter = factory.createCodeSnippetStatement(String.format("%s.registerConverter(new FileCleanableConverter())", XSTREAM_VARIABLE));
        CtStatement registerInflaterConverter = factory.createCodeSnippetStatement(String.format("%s.registerConverter(new InflaterConverter())", XSTREAM_VARIABLE));
        CtStatement registerCleanerImplConverter = factory.createCodeSnippetStatement(String.format("%s.registerConverter(new CleanerImplConverter())", XSTREAM_VARIABLE));
        methodBody.addStatement(registerFileConverter);
        methodBody.addStatement(registerInflaterConverter);
        methodBody.addStatement(registerCleanerImplConverter);
        generatedMethod.setBody(methodBody);
        return generatedMethod;
    }

    public CtMethod<?> generateTestMethod(CtMethod<?> method,
                                          int methodCounter,
                                          InstrumentedMethod instrumentedMethod,
                                          SerializedObject serializedObject) throws ClassNotFoundException {
        CtMethod<?> generatedMethod = factory.createMethod();
        String postfix = "";
        if (instrumentedMethod.isOverloaded()) {
            postfix = TestGeneratorUtil.getParamListPostFix(instrumentedMethod.getParamList())
                    .replaceAll("\\[]", "_arr")
                    .replaceAll("[.,]", "_");
        }
        generatedMethod.setSimpleName("test" + method.getSimpleName().substring(0, 1).toUpperCase() + method.getSimpleName().substring(1) + postfix + methodCounter);
        generatedMethod.addAnnotation(testAnnotation);
        if (generateMocks) {
            generatedMethod.addAnnotation(disabledAnnotation);
        }
        generatedMethod.setModifiers(Collections.singleton(ModifierKind.PUBLIC));
        generatedMethod.setType(factory.createCtTypeReference(void.class));
        generatedMethod.addThrownType(factory.createCtTypeReference(Exception.class));

        // Get serialized objects as XML strings
        String receivingObjectStr = serializedObject.getReceivingObject();
        String receivingObjectType = serializedObject.getObjectType(receivingObjectStr);
        String returnedObjectStr = serializedObject.getReturnedObject();
        String returnedObjectType = instrumentedMethod.getReturnType();
        String receivingPostObjectStr = serializedObject.getReceivingPostObject();

        String paramsObjectStr = "";
        if (instrumentedMethod.hasParams()) {
            paramsObjectStr = serializedObject.getParamObjects();
        }

        if (this.testFormat.equals("json")) {
            receivingObjectStr = testGenUtil.transformXML2JSON(receivingObjectStr);
            returnedObjectStr = testGenUtil.transformXML2JSON(returnedObjectStr);
            if (paramsObjectStr.length() > 0) {
                paramsObjectStr = testGenUtil.transformXML2JSON(paramsObjectStr);
            }
        }

        CtBlock<?> methodBody = factory.createBlock();

        List<CtStatement> statementsInMethodBody =
                generateStatementsInMethodBody(instrumentedMethod, method, methodCounter, serializedObject,
                        receivingObjectStr, receivingObjectType, returnedObjectStr, returnedObjectType, receivingPostObjectStr,
                        paramsObjectStr);

        statementsInMethodBody.forEach(methodBody::addStatement);
        generatedMethod.setBody(methodBody);
        return generatedMethod;
    }

    /**
     * We generate three categories of tests for each invocation:
     * <p>Output oracle (OO): Stubbing non-void mockable(s) + assert the output of the MUT</p>
     * <p>Parameter oracle (PO): Stub non-void mockable(s) + verify parameters of mockables</p>
     * <p>Call oracle (CO): Stub non-void mockable(s) + verify sequence and frequency of mockables</p>
     *
     * @param instrumentedMethod
     * @param serializedObject
     * @param mockMethod
     * @param generatedClass
     * @throws ClassNotFoundException
     */
    public void generateMockMethods(InstrumentedMethod instrumentedMethod,
                                    SerializedObject serializedObject,
                                    CtMethod<?> mockMethod,
                                    CtClass<?> generatedClass) throws ClassNotFoundException {
        // If mocks can be used to test this method
        if (instrumentedMethod.hasMockableInvocations()) {
            MockGenerator mockGenerator = new MockGenerator(factory);

            // Annotate generated class with @ExtendWith(MockitoExtension.class)
            if (generatedClass.getAnnotations()
                    .stream().noneMatch(ctAnnotation -> ctAnnotation.toString().contains("ExtendWith")))
                mockGenerator.addAnnotationToGeneratedClass(generatedClass);

            // Add @Mock, @InjectMocks fields, generate tests that use mocks
            Set<CtMethod<?>> generatedTestsWithMocks = new LinkedHashSet<>();

            // Test - OO
            if (!instrumentedMethod.getReturnType().equals("void") &
                    MockGeneratorUtil.arePrimitiveOrString(List.of(instrumentedMethod.getReturnType()))) {
                CtMethod<?> testOO = mockGenerator.generateTestByCategory("OO", mockMethod, serializedObject, generatedClass, instrumentedMethod);
                generatedClass.addMethod(testOO);
                generatedTestsWithMocks.add(testOO);
                System.out.println("Generated 1 OO test for " + instrumentedMethod.getFullMethodPath());
            }

            // Test - PO
            CtMethod<?> testPO = mockGenerator.generateTestByCategory("PO", mockMethod, serializedObject, generatedClass, instrumentedMethod);
            generatedClass.addMethod(testPO);
            generatedTestsWithMocks.add(testPO);
            System.out.println("Generated 1 PO test for " + instrumentedMethod.getFullMethodPath());

            String uuid = serializedObject.getUUID().replace("-", "");

            // Test - CO
            String postfix = "";
            if (instrumentedMethod.isOverloaded()) {
                postfix = TestGeneratorUtil.getParamListPostFix(instrumentedMethod.getParamList())
                        .replaceAll("\\[]", "_arr")
                        .replaceAll("[.,]", "_");
            }
            String methodNameCO = String.format("test_%s%s_CO_%s", instrumentedMethod.getMethodName(), postfix, uuid);
            if (generatedClass.getMethodsByName(methodNameCO).size() == 0 &
                    serializedObject.getNestedSerializedObjects().size() > 0) {
                MethodSequenceGenerator sequenceGenerator = new MethodSequenceGenerator(factory);
                CtMethod<?> testCO = sequenceGenerator.generateTestToVerifyMethodSequence(Set.of(testPO), serializedObject);
                testCO.setSimpleName(methodNameCO);
                testCO.addAnnotation(testAnnotation);
                generatedClass.addMethod(testCO);
                generatedTestsWithMocks.add(testCO);
                System.out.println("Generated 1 CO test for " + instrumentedMethod.getFullMethodPath());
            }

            numberOfTestCasesWithMocksGenerated += generatedTestsWithMocks.size();

            // Add comments
            CtComment arrangeComment = factory.createInlineComment("Arrange");
            CtComment actComment = factory.createInlineComment("Act");
            CtComment assertComment = factory.createInlineComment("Assert");

            for (CtMethod<?> generatedTestWithMock : generatedTestsWithMocks) {
                if (!generatedTestWithMock.getBody().getStatement(0).getComments().contains(arrangeComment))
                    generatedTestWithMock.getBody().getStatement(0).addComment(arrangeComment);
                int indexWithCall = MockGeneratorUtil.findIndexOfStatementWithInvocationOnReceivingObject(generatedTestWithMock);
                generatedTestWithMock.getBody().getStatement(indexWithCall).addComment(actComment);
                int indexWithOracle = MockGeneratorUtil.findIndexOfStatementWithAssertionOrVerification(generatedTestWithMock);
                generatedTestWithMock.getBody().getStatement(indexWithOracle).addComment(assertComment);
                generatedClass.addMethod(generatedTestWithMock);
            }
        }
    }

    public CtClass<?> generateFullTestClass(CtType<?> type,
                                            CtMethod<?> method,
                                            InstrumentedMethod instrumentedMethod,
                                            String objectXMLDirectoryPath) throws ClassNotFoundException {
        ObjectXMLParser objectXMLParser = new ObjectXMLParser();
        Set<SerializedObject> serializedObjects = objectXMLParser.parseXML(
                objectXMLDirectoryPath + File.separatorChar, instrumentedMethod, generateMocks);
        System.out.println("Number of unique pairs/triples of object values: " + serializedObjects.size());

        if (serializedObjects.size() == 0) {
            System.out.println("NO OBJECTS FOUND FOR " + instrumentedMethod.getFullMethodPath() + " - SKIPPING");
            return null;
        } else if (generateMocks & serializedObjects.stream().noneMatch(s -> s.getNestedSerializedObjects().size() > 0)) {
            System.out.println("NO MOCKABLE INVOCATIONS FOUND FOR " + instrumentedMethod.getFullMethodPath() + " - SKIPPING");
            return null;
        } else {
            factory = type.getFactory();
            generateTestAndDisabledAnnotations();
            CtClass<?> generatedClass = factory.Class().get(getGeneratedClassName(type.getPackage(), type.getSimpleName()));
            if (generatedClass == null) {
                generatedClass = generateTestClass(type.getPackage(), type.getSimpleName());
                generatedClass.addField(addXStreamFieldToGeneratedClass());
                addDeserializationMethodsToTestClass().forEach(generatedClass::addMethod);
            }

            // Create @Before method
            // generatedClass.addMethod(generateSetupMethod());

            // Create @Test method
            int methodCounter = 1;
            for (SerializedObject serializedObject : serializedObjects) {
                CtMethod<?> generatedMethod = generateTestMethod(method, methodCounter, instrumentedMethod, serializedObject);
                if (!generateMocks)
                    generatedClass.addMethod(generatedMethod);
                // If mocks can be used to test this method
                if (generateMocks & serializedObject.getNestedSerializedObjects().size() > 0) {
                    CtMethod<?> baseMethod = generatedMethod.clone();
                    generateMockMethods(instrumentedMethod, serializedObject, baseMethod, generatedClass);
                }
                methodCounter++;
            }
            if (generatedClass.getMethods().stream().anyMatch(m -> m.getAnnotations().contains(testAnnotation) &
                    !m.getAnnotations().contains(disabledAnnotation)))
                return generatedClass;
            else {
                System.out.println("No non-disabled test, not generating class " + generatedClass.getQualifiedName());
                factory.Class().get(generatedClass.getQualifiedName()).delete();
                return null;
            }
        }
    }

    public List<CtType<?>> getTypesToProcess(CtModel ctModel) {
        List<CtType<?>> types = ctModel.getAllTypes().stream().
                filter(ctType -> ctType.isClass() || ctType.isEnum()).
                collect(Collectors.toList());
        List<CtType<?>> typesToProcess = new ArrayList<>(types);
        for (CtType<?> type : types) {
            typesToProcess.addAll(type.getNestedTypes());
        }
        return typesToProcess;
    }

    public Map.Entry<CtMethod<?>, Boolean> findMethodToGenerateTestMethodsFor(List<CtMethod<?>> methodsByName,
                                                                              InstrumentedMethod instrumentedMethod) {
        if (methodsByName.size() > 1) {
            // match parameter list for overloaded methods
            for (CtMethod<?> method : methodsByName) {
                List<String> paramTypes = method.getParameters().stream().
                        map(parameter -> parameter.getType().getQualifiedName()).
                        collect(Collectors.toList());
                if (Arrays.equals(paramTypes.toArray(), instrumentedMethod.getParamList().toArray())) {
                    System.out.println("matched params " + paramTypes + " for overloaded method " +
                            instrumentedMethod.getFullMethodPath());
                    return new AbstractMap.SimpleEntry<>(method, true);
                }
            }
        }
        return new AbstractMap.SimpleEntry<>(methodsByName.get(0), false);
    }

    public int getNumberOfTestCasesWithMocksGenerated() {
        return numberOfTestCasesWithMocksGenerated;
    }

    public int process(CtModel ctModel, String methodCSVFilePath, String objectXMLDirectoryPath) {
        // Get list of instrumented methods from CSV file
        List<InstrumentedMethod> instrumentedMethods = CSVFileParser.parseCSVFile(methodCSVFilePath);
        System.out.println("Number of instrumented methods: " + instrumentedMethods.size());
        System.out.println("--------------------------------------------------------------");
        List<CtType<?>> types = getTypesToProcess(ctModel);

        for (CtType<?> type : types) {
            for (InstrumentedMethod instrumentedMethod : instrumentedMethods) {
                if (type.getQualifiedName().equals(instrumentedMethod.getParentFQN())) {
                    List<CtMethod<?>> methodsByName = type.getMethodsByName(instrumentedMethod.getMethodName());
                    if (methodsByName.size() > 0) {
                        Map.Entry<CtMethod<?>, Boolean> methodAndOverload = findMethodToGenerateTestMethodsFor(methodsByName, instrumentedMethod);
                        CtMethod<?> methodToGenerateTestsFor = methodAndOverload.getKey();
                        instrumentedMethod.setOverloaded(methodAndOverload.getValue());
                        System.out.println("Generating test method for: " +
                                instrumentedMethod.getParentFQN() + "." + instrumentedMethod.getMethodName());
                        try {
                            CtClass<?> generatedClass = generateFullTestClass(
                                    type, methodToGenerateTestsFor, instrumentedMethod, objectXMLDirectoryPath);
                            if (generatedClass != null) {
                                System.out.println("Generated test class: " + generatedClass.getQualifiedName());
                                generatedTestClasses.add(generatedClass);
                            }
                            System.out.println("--------------------------------------------------------------");
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        int allGeneratedTests = 0;
        for (CtClass<?> generated : generatedTestClasses) {
            allGeneratedTests += generated.getMethods().stream()
                    .filter(m -> m.getAnnotations().contains(testAnnotation)).count();
        }
        return allGeneratedTests - numberOfTestCasesWithMocksGenerated;
    }
}
