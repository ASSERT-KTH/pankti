package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.CSVFileParser;
import se.kth.castor.pankti.generate.parsers.InstrumentedMethod;
import se.kth.castor.pankti.generate.parsers.ObjectJSONParser;
import se.kth.castor.pankti.generate.parsers.SerializedObject;
import spoon.MavenLauncher;
import spoon.compiler.SpoonResource;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class TestGenerator {
    private static Factory factory;
    private static final String XSTREAM_REFERENCE = "com.thoughtworks.xstream.XStream";
    private static final String XSTREAM_DRIVER_REFERENCE = "com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver";
    private static final String XSTREAM_CONSTRUCTOR = "new XStream(new JettisonMappedXmlDriver())";
    private static final String JUNIT_TEST_REFERENCE = "org.junit.Test";
    private static final String JUNIT_BEFORE_REFERENCE = "org.junit.Before";
    private static final String JUNIT_ASSERT_REFERENCE = "org.junit.Assert";
    private static final String JAVA_UTIL_ARRAYS_REFERENCE = "java.util.Arrays";
    private static final String JAVA_UTIL_SCANNER_REFERENCE = "java.util.Scanner";
    private static final String JAVA_IO_FILE_REFERENCE = "java.io.File";

    private static final String TEST_CLASS_PREFIX = "Test";
    private static final String TEST_CLASS_POSTFIX = "PanktiGen";
    private static int numberOfTestCasesGenerated;

    private final TestGeneratorUtil testGenUtil = new TestGeneratorUtil();

    public String getGeneratedClassName(CtPackage ctPackage, String className) {
        return String.format("%s.%s%s%s", ctPackage, TEST_CLASS_PREFIX, className, TEST_CLASS_POSTFIX);
    }

    public CtClass<?> generateTestClass(CtPackage ctPackage, String className) {
        CtClass<?> generatedClass = factory.createClass(ctPackage, TEST_CLASS_PREFIX + className + TEST_CLASS_POSTFIX);
        generatedClass.addModifier(ModifierKind.PUBLIC);
        return generatedClass;
    }

    public void addImportsToGeneratedClass(CtClass<?> generatedClass) {
        generatedClass.getFactory().createUnresolvedImport(XSTREAM_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(XSTREAM_DRIVER_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JUNIT_TEST_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JUNIT_BEFORE_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JUNIT_ASSERT_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_UTIL_ARRAYS_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_UTIL_SCANNER_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_IO_FILE_REFERENCE, false);
    }

    public CtField<?> addXStreamFieldToGeneratedClass() throws ClassNotFoundException {
        CtField<?> xStreamField = factory.createCtField(
                "xStream",
                factory.createCtTypeReference(Class.forName(XSTREAM_REFERENCE)),
                XSTREAM_CONSTRUCTOR
        );
        xStreamField.addModifier(ModifierKind.STATIC);
        return xStreamField;
    }

    @SuppressWarnings("unchecked")
    public CtInvocation<?> generateAssertionInTestMethod(CtMethod<?> method, SerializedObject serializedObject) throws ClassNotFoundException {
        CtExpression<?> assertExpectedObject;
        if (method.getType().isPrimitive()) {
            String value = serializedObject.getReturnedObject(true);
            assertExpectedObject = factory.createCodeSnippetExpression(value);
        } else {
            assertExpectedObject = factory.createCodeSnippetExpression("expectedObject");
        }

        StringBuilder arguments = new StringBuilder();
        for (int i = 1; i <= method.getParameters().size(); i++) {
            arguments.append("paramObject").append(i);
            if (i != method.getParameters().size()) {
                arguments.append(", ");
            }
        }

        CtExpression<?> assertActualObject = factory.createCodeSnippetExpression(
                String.format("receivingObject.%s(%s)",
                        method.getSimpleName(),
                        arguments.toString()));

        if (method.getVisibility().equals(ModifierKind.PRIVATE)) {
            assertActualObject = factory.createCodeSnippetExpression(
                    String.format("%s%s.invoke(%s, %s)",
                            method.getType().isArray() ? "(" + method.getType() + ") " : "",
                            method.getSimpleName(),
                            "receivingObject",
                            arguments.toString()));
        }

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
        return assertInvocation;
    }

    public String createLongJSONStringFile(String methodIdentifier, String type, String longJSON, MavenLauncher launcher) {
        String fileName = "";
        try {
            File longJSONFile = new File("./output/object-data/" + methodIdentifier + "-" + type + ".json");
            longJSONFile.getParentFile().mkdirs();
            FileWriter myWriter = new FileWriter(longJSONFile);
            myWriter.write(longJSON);
            myWriter.close();
            SpoonResource newResource = SpoonResourceHelper.createResource(longJSONFile);
            launcher.addInputResource(longJSONFile.getAbsolutePath());
            fileName = newResource.getName();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileName;
    }

    public List<CtStatement> readJSONFromFile(String fileName, String type) {
        List<CtStatement> createScannerReadString = new ArrayList<>();
        List<CtStatement> scannerDeclaration = testGenUtil.addScannerVariableToTestMethod(factory, fileName, type);
        CtStatement stringReadFromScanner = testGenUtil.readStringFromScanner(factory, type);
        createScannerReadString.addAll(scannerDeclaration);
        createScannerReadString.add(stringReadFromScanner);
        return createScannerReadString;
    }

    public CtStatement parseReceivingObject(String receivingObjectType) {
        return factory.createCodeSnippetStatement(String.format(
            "%s receivingObject = (%s) xStream.fromXML(receivingJSON)",
            receivingObjectType,
            receivingObjectType));
    }

    public CtStatement parseReturnedObject(String returnedObjectType, CtMethod<?> method) {
        return factory.createCodeSnippetStatement(String.format(
            "%s expectedObject = (%s) xStream.fromXML(returnedJSON)",
            returnedObjectType,
            testGenUtil.findObjectBoxType(method.getType())));
    }

    public List<CtStatement> addAndParseMethodParams(String paramsJSON, CtMethod<?> method) {
        List<CtStatement> paramStatements = new ArrayList<>();
        if (paramsJSON.length() <= 10000) {
            CtStatement paramsJSONStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "paramsJSON", paramsJSON);
            paramStatements.add(paramsJSONStringDeclaration);
        }
        CtStatement parseParamObjects = factory.createCodeSnippetStatement(
            String.format(
                "%s paramObjects = (%s) xStream.fromXML(paramsJSON)",
                "Object[]",
                "Object[]"));

        paramStatements.add(parseParamObjects);

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
                                                            String receivingJSON,
                                                            String receivingObjectType,
                                                            String returnedJSON,
                                                            String returnedObjectType,
                                                            String paramsJSON,
                                                            MavenLauncher launcher) throws ClassNotFoundException {
        List<CtStatement> methodBody = new ArrayList<>();
        String postfix = "";
        if (instrumentedMethod.isOverloaded()) {
            postfix = testGenUtil.getParamListPostFix(instrumentedMethod);
        }
        String methodIdentifier = instrumentedMethod.getFullMethodPath() + postfix + methodCounter;
        if (receivingJSON.length() > 10000 || returnedJSON.length() > 10000 || paramsJSON.length() > 10000) {
            CtStatement classLoaderDeclaration = testGenUtil.addClassLoaderVariableToTestMethod(factory);
            methodBody.add(classLoaderDeclaration);
        }
        if (receivingJSON.length() > 10000) {
            String type = "receiving";
            String fileName = createLongJSONStringFile(methodIdentifier, type, receivingJSON, launcher);
            List<CtStatement> createScannerReadJSON = readJSONFromFile(fileName, type);
            methodBody.addAll(createScannerReadJSON);
        } else {
            CtStatement receivingJSONStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "receivingJSON", receivingJSON);
            methodBody.add(receivingJSONStringDeclaration);
        }
        methodBody.add(parseReceivingObject(receivingObjectType));
        if (returnedJSON.length() > 10000) {
            String type = "returned";
            String fileName = createLongJSONStringFile(methodIdentifier, type, returnedJSON, launcher);
            List<CtStatement> createScannerReadJSON = readJSONFromFile(fileName, type);
            methodBody.addAll(createScannerReadJSON);
        } else {
            if (!method.getType().isPrimitive()) {
                CtStatement returnedJSONStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "returnedJSON", returnedJSON);
                methodBody.add(returnedJSONStringDeclaration);
            }
        }
        if (!method.getType().isPrimitive())
            methodBody.add(parseReturnedObject(returnedObjectType, method));

        if (!paramsJSON.isEmpty()) {
            if (paramsJSON.length() > 10000) {
                String type = "params";
                String fileName = createLongJSONStringFile(methodIdentifier, type, paramsJSON, launcher);
                List<CtStatement> createScannerReadJSON = readJSONFromFile(fileName, type);
                methodBody.addAll(createScannerReadJSON);
            }
            List<CtStatement> paramStatements = addAndParseMethodParams(paramsJSON, method);
            methodBody.addAll(paramStatements);
        }
        if (instrumentedMethod.getVisibility().equals("private")) {
            methodBody.addAll(accessPrivateMethod(instrumentedMethod));
        }

        methodBody.add(generateAssertionInTestMethod(method, serializedObject));
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
        CtStatement registerFileConverter = factory.createCodeSnippetStatement("xStream.registerConverter(new FileCleanableConverter())");
        CtStatement registerInflaterConverter = factory.createCodeSnippetStatement("xStream.registerConverter(new InflaterConverter())");
        CtStatement registerCleanerImplConverter = factory.createCodeSnippetStatement("xStream.registerConverter(new CleanerImplConverter())");
        methodBody.addStatement(registerFileConverter);
        methodBody.addStatement(registerInflaterConverter);
        methodBody.addStatement(registerCleanerImplConverter);
        generatedMethod.setBody(methodBody);
        return generatedMethod;
    }

    public CtMethod<?> generateTestMethod(CtMethod<?> method,
                                          int methodCounter,
                                          InstrumentedMethod instrumentedMethod,
                                          SerializedObject serializedObject,
                                          MavenLauncher launcher) throws ClassNotFoundException {
        CtMethod<?> generatedMethod = factory.createMethod();
        String postfix = "";
        if (instrumentedMethod.isOverloaded()) {
            postfix = testGenUtil.getParamListPostFix(instrumentedMethod).replaceAll("[.,]", "_");
        }
        generatedMethod.setSimpleName("test" + method.getSimpleName().substring(0, 1).toUpperCase() + method.getSimpleName().substring(1) + postfix + methodCounter);
        CtAnnotation<?> testAnnotation = factory.createAnnotation(factory.createCtTypeReference(Class.forName(JUNIT_TEST_REFERENCE)));
        generatedMethod.addAnnotation(testAnnotation);
        generatedMethod.setModifiers(Collections.singleton(ModifierKind.PUBLIC));
        generatedMethod.setType(factory.createCtTypeReference(void.class));
        generatedMethod.addThrownType(factory.createCtTypeReference(Exception.class));

        // Get serialized objects as JSON strings
        String receivingJSON = serializedObject.getReceivingObject();
        String receivingObjectType = serializedObject.getObjectType(receivingJSON);
        String returnedJSON = serializedObject.getReturnedObject(false);
        String returnedObjectType = instrumentedMethod.getReturnType();

        String paramsJSON = "";
        if (instrumentedMethod.hasParams()) {
            paramsJSON = serializedObject.getParamObjects();
        }

        CtBlock<?> methodBody = factory.createBlock();

        List<CtStatement> statementsInMethodBody =
                generateStatementsInMethodBody(instrumentedMethod, method, methodCounter, serializedObject,
                        receivingJSON, receivingObjectType, returnedJSON, returnedObjectType, paramsJSON, launcher);

        statementsInMethodBody.forEach(methodBody::addStatement);
        generatedMethod.setBody(methodBody);
        return generatedMethod;
}

    public CtClass<?> generateFullTestClass(CtType<?> type,
                                            CtMethod<?> method,
                                            InstrumentedMethod instrumentedMethod,
                                            MavenLauncher launcher,
                                            String objectJSONDirectoryPath) throws ClassNotFoundException {
        factory = type.getFactory();
        CtClass<?> generatedClass = factory.Class().get(getGeneratedClassName(type.getPackage(), type.getSimpleName()));
        if (generatedClass == null) {
            generatedClass = generateTestClass(type.getPackage(), type.getSimpleName());
            addImportsToGeneratedClass(generatedClass);
            generatedClass.addField(addXStreamFieldToGeneratedClass());
        }
        String methodPath = instrumentedMethod.getFullMethodPath();
        ObjectJSONParser objectJSONParser = new ObjectJSONParser();
        Set<SerializedObject> serializedObjects = objectJSONParser.parseJSON(
                objectJSONDirectoryPath + File.separatorChar + methodPath, instrumentedMethod);
        System.out.println("Number of unique pairs/triples of object values: " + serializedObjects.size());
        numberOfTestCasesGenerated += serializedObjects.size();

        // Create @Before method
        // generatedClass.addMethod(generateSetupMethod());

        // Create @Test method
        int methodCounter = 1;
        for (SerializedObject serializedObject : serializedObjects) {
            CtMethod<?> generatedMethod = generateTestMethod(method, methodCounter, instrumentedMethod, serializedObject, launcher);
            generatedClass.addMethod(generatedMethod);
            methodCounter++;
        }
        return generatedClass;
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

    private Map.Entry<CtMethod<?>, Boolean> findMethodToGenerateTestMethodsFor(List<CtMethod<?>> methodsByName, InstrumentedMethod instrumentedMethod) {
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

    public int process(CtModel ctModel, MavenLauncher launcher, String methodCSVFilePath, String objectJSONDirectoryPath) {
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
                        System.out.println("Generating test method for: " + methodToGenerateTestsFor.getPath());
                        try {
                            CtClass<?> generatedClass = generateFullTestClass(
                                    type, methodToGenerateTestsFor, instrumentedMethod,
                                    launcher, objectJSONDirectoryPath);
                            System.out.println("Generated test class: " + generatedClass.getQualifiedName());
                            System.out.println("--------------------------------------------------------------");
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return numberOfTestCasesGenerated;
    }
}
