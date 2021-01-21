package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.CSVFileParser;
import se.kth.castor.pankti.generate.parsers.InstrumentedMethod;
import se.kth.castor.pankti.generate.parsers.ObjectXMLParser;
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
    private static final String XSTREAM_CONSTRUCTOR = "new XStream()";
    private static final String XSTREAM_VARIABLE = "xStream";
    private static final String JUNIT_TEST_REFERENCE = "org.junit.Test";
    private static final String JUNIT_BEFORE_REFERENCE = "org.junit.Before";
    private static final String JUNIT_ASSERT_REFERENCE = "org.junit.Assert";
    private static final String JAVA_UTIL_ARRAYS_REFERENCE = "java.util.Arrays";
    private static final String JAVA_UTIL_SCANNER_REFERENCE = "java.util.Scanner";
    private static final String JAVA_IO_FILE_REFERENCE = "java.io.File";

    private static final String TEST_CLASS_PREFIX = "Test";
    private static final String TEST_CLASS_POSTFIX = "PanktiGen";
    private static int numberOfTestCasesGenerated;
    private String testFormat;

    private final TestGeneratorUtil testGenUtil = new TestGeneratorUtil();

    public TestGenerator(String testFormat) {
        this.testFormat = testFormat;
    }

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
        if (this.testFormat.equals("json")) {
            generatedClass.getFactory().createUnresolvedImport(XSTREAM_DRIVER_REFERENCE, false);
        }
        generatedClass.getFactory().createUnresolvedImport(JUNIT_TEST_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JUNIT_BEFORE_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JUNIT_ASSERT_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_UTIL_ARRAYS_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_UTIL_SCANNER_REFERENCE, false);
        generatedClass.getFactory().createUnresolvedImport(JAVA_IO_FILE_REFERENCE, false);
    }

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

        CtMethod<?> deserializeObjectFromString = testGenUtil.generateDeserializationMethod(factory);
        CtParameter<?> parameter1 = factory.createParameter();
        parameter1.setType(factory.createCtTypeReference(String.class));
        parameter1.setSimpleName("serializedObjectString");
        deserializeObjectFromString.setParameters(Collections.singletonList(parameter1));

        CtMethod<?> deserializeObjectFromFile = testGenUtil.generateDeserializationMethod(factory);
        deserializeObjectFromFile.addThrownType(factory.createCtTypeReference(Exception.class));
        CtParameter<?> parameter2 = factory.createParameter();
        parameter2.setType(factory.createCtTypeReference(File.class));
        parameter2.setSimpleName("serializedObjectFile");
        deserializeObjectFromFile.setParameters(Collections.singletonList(parameter2));
        CtBlock<?> methodBody = deserializeObjectFromFile.getBody();
        methodBody.insertBegin(testGenUtil.addAndReadFromScannerInDeserializationMethod(factory));
        deserializeObjectFromFile.setBody(methodBody);

        deserializationMethods.add(deserializeObjectFromString);
        deserializationMethods.add(deserializeObjectFromFile);
        return deserializationMethods;
    }

    @SuppressWarnings("unchecked")
    public List<CtStatement> generateAssertionInTestMethod(CtMethod<?> method, SerializedObject serializedObject) throws ClassNotFoundException {
        List<CtStatement> assertionStatements = new ArrayList<>();
        CtExpression<?> assertExpectedObject;
        if (method.getType().getSimpleName().equals("void")) {
            assertExpectedObject = factory.createCodeSnippetExpression(String.format("%s.toXML(receivingObjectPost)", XSTREAM_VARIABLE));
        } else if (method.getType().isPrimitive()) {
            String value = serializedObject.getReturnedObject().replaceAll("</?\\w+>", "");
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
                arguments.toString());
        if (method.getType().getSimpleName().equals("void")) {
            CtStatement methodInvocation = factory.createCodeSnippetStatement(assertionStatement);
            assertionStatements.add(methodInvocation);
            assertActualObject = factory.createCodeSnippetExpression(String.format("%s.toXML(receivingObject)", XSTREAM_VARIABLE));
        } else {
            assertActualObject = factory.createCodeSnippetExpression(assertionStatement);
        }

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
        assertionStatements.add(assertInvocation);
        return assertionStatements;
    }

    public String createLongObjectStringFile(String methodIdentifier, String profileType, String longObjectStr, MavenLauncher launcher) {
        String fileName = "";
        try {
            File longObjectStrFile = new File("./output/object-data/" + methodIdentifier + "-" + profileType + "." + this.testFormat);
            longObjectStrFile.getParentFile().mkdirs();
            FileWriter myWriter = new FileWriter(longObjectStrFile);
            myWriter.write(longObjectStr);
            myWriter.close();
            SpoonResource newResource = SpoonResourceHelper.createResource(longObjectStrFile);
            launcher.addInputResource(longObjectStrFile.getAbsolutePath());
            fileName = newResource.getName();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileName;
    }

    public CtStatement parseReceivingObjectFromFileOrString(String receivingObjectType, String fileOrString) {
        return factory.createCodeSnippetStatement(String.format(
                "%s receivingObject = deserializeObject(%s)",
                receivingObjectType,
                fileOrString));
    }

    public CtStatement parseReceivingObjectPostFromFileOrString(String receivingObjectType, String fileOrString) {
        return factory.createCodeSnippetStatement(String.format(
                "%s receivingObjectPost = deserializeObject(%s)",
                receivingObjectType,
                fileOrString));
    }

    public CtStatement parseReturnedObjectFromFileOrString(String returnedObjectType, String fileOrString) {
        return factory.createCodeSnippetStatement(String.format(
                "%s expectedObject = deserializeObject(%s)",
                returnedObjectType,
                fileOrString));
    }

    public CtStatement parseParamObjectsFromFileOrString(String fileOrString) {
        return factory.createCodeSnippetStatement(String.format(
                "Object[] paramObjects = deserializeObject(%s)",
                fileOrString));
    }

    public List<CtStatement> addAndParseMethodParams(String paramsObjectStr, CtMethod<?> method) {
        List<CtStatement> paramStatements = new ArrayList<>();
        if (paramsObjectStr.length() <= 10000) {
            CtStatement paramsXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "paramsObjectStr", paramsObjectStr);
            paramStatements.add(paramsXMLStringDeclaration);
            CtStatement parseParamObjectsFromString = parseParamObjectsFromFileOrString("paramsObjectStr");
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
                                                            String paramsObjectStr,
                                                            MavenLauncher launcher) throws ClassNotFoundException {
        List<CtStatement> methodBody = new ArrayList<>();
        String postfix = "";
        if (instrumentedMethod.isOverloaded()) {
            postfix = testGenUtil.getParamListPostFix(instrumentedMethod);
        }
        String methodIdentifier = instrumentedMethod.getFullMethodPath() + postfix + methodCounter;
        if (receivingObjectStr.length() > 10000 || returnedObjectStr.length() > 10000 || receivingObjectPostStr.length() > 10000 || paramsObjectStr.length() > 10000) {
            CtStatement classLoaderDeclaration = testGenUtil.addClassLoaderVariableToTestMethod(factory);
            methodBody.add(classLoaderDeclaration);
        }

        if (receivingObjectStr.length() > 10000) {
            String type = "receiving";
            String fileName = createLongObjectStringFile(methodIdentifier, type, receivingObjectStr, launcher);
            CtStatement fileVariableDeclaration = testGenUtil.addFileVariableToTestMethod(factory, fileName, type);
            CtStatement parseReceivingObjectFromFile = parseReceivingObjectFromFileOrString(receivingObjectType, "file" + testGenUtil.getObjectProfileType(type));
            methodBody.add(fileVariableDeclaration);
            methodBody.add(parseReceivingObjectFromFile);
        } else {
            CtStatement receivingXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "receivingObjectStr", receivingObjectStr);
            CtStatement parseReceivingObjectFromString = parseReceivingObjectFromFileOrString(receivingObjectType, "receivingObjectStr");
            methodBody.add(receivingXMLStringDeclaration);
            methodBody.add(parseReceivingObjectFromString);
        }

        if (method.getType().getSimpleName().equals("void")) {
            if (receivingObjectPostStr.length() > 10000) {
                String type = "receivingpost";
                String fileName = createLongObjectStringFile(methodIdentifier, type, receivingObjectPostStr, launcher);
                CtStatement fileVariableDeclaration = testGenUtil.addFileVariableToTestMethod(factory, fileName, type);
                CtStatement parseReceivingPostObjectPostFromFile = parseReceivingObjectPostFromFileOrString(receivingObjectType, "file" + testGenUtil.getObjectProfileType(type));
                methodBody.add(fileVariableDeclaration);
                methodBody.add(parseReceivingPostObjectPostFromFile);
            } else {
                CtStatement receivingPostXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "receivingPostObjectStr", receivingObjectPostStr);
                CtStatement parseReceivingPostObjectFromString = parseReceivingObjectPostFromFileOrString(receivingObjectType, "receivingPostObjectStr");
                methodBody.add(receivingPostXMLStringDeclaration);
                methodBody.add(parseReceivingPostObjectFromString);
            }
        } else {
            if (returnedObjectStr.length() > 10000) {
                String type = "returned";
                String fileName = createLongObjectStringFile(methodIdentifier, type, returnedObjectStr, launcher);
                CtStatement fileVariableDeclaration = testGenUtil.addFileVariableToTestMethod(factory, fileName, type);
                CtStatement parseReturnedObjectFromFile = parseReturnedObjectFromFileOrString(returnedObjectType, "file" + testGenUtil.getObjectProfileType(type));
                methodBody.add(fileVariableDeclaration);
                methodBody.add(parseReturnedObjectFromFile);
            } else {
                if (!method.getType().isPrimitive() & !testGenUtil.returnedObjectIsNull(returnedObjectStr)) {
                    CtStatement returnedXMLStringDeclaration = testGenUtil.addStringVariableToTestMethod(factory, "returnedObjectStr", returnedObjectStr);
                    methodBody.add(returnedXMLStringDeclaration);
                    CtStatement parseReturnedObjectFromString = parseReturnedObjectFromFileOrString(returnedObjectType, "returnedObjectStr");
                    methodBody.add(parseReturnedObjectFromString);
                }
            }
        }

        if (!paramsObjectStr.isEmpty()) {
            if (paramsObjectStr.length() > 10000) {
                String type = "params";
                String fileName = createLongObjectStringFile(methodIdentifier, type, paramsObjectStr, launcher);
                CtStatement fileVariableDeclaration = testGenUtil.addFileVariableToTestMethod(factory, fileName, type);
                CtStatement parseParamObjectsFromFile = parseParamObjectsFromFileOrString("file" + testGenUtil.getObjectProfileType(type));
                methodBody.add(fileVariableDeclaration);
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
                        paramsObjectStr, launcher);

        statementsInMethodBody.forEach(methodBody::addStatement);
        generatedMethod.setBody(methodBody);
        return generatedMethod;
    }

    public CtClass<?> generateFullTestClass(CtType<?> type,
                                            CtMethod<?> method,
                                            InstrumentedMethod instrumentedMethod,
                                            MavenLauncher launcher,
                                            String objectXMLDirectoryPath) throws ClassNotFoundException {
        String methodPath = instrumentedMethod.getFullMethodPath();
        ObjectXMLParser objectXMLParser = new ObjectXMLParser();
        Set<SerializedObject> serializedObjects = objectXMLParser.parseXML(
                objectXMLDirectoryPath + File.separatorChar + methodPath, instrumentedMethod);
        System.out.println("Number of unique pairs/triples of object values: " + serializedObjects.size());

        if (serializedObjects.size() == 0) {
            System.out.println("NO OBJECTS FOUND FOR " + instrumentedMethod.getFullMethodPath() + " - SKIPPING");
            return null;
        } else {
            numberOfTestCasesGenerated += serializedObjects.size();

            factory = type.getFactory();
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
                CtMethod<?> generatedMethod = generateTestMethod(method, methodCounter, instrumentedMethod, serializedObject, launcher);
                generatedClass.addMethod(generatedMethod);
                methodCounter++;
            }
            return generatedClass;
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

    public int process(CtModel ctModel, MavenLauncher launcher, String methodCSVFilePath, String objectXMLDirectoryPath) {
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
                                    type, methodToGenerateTestsFor, instrumentedMethod,
                                    launcher, objectXMLDirectoryPath);
                            if (generatedClass != null) {
                                System.out.println("Generated test class: " + generatedClass.getQualifiedName());
                            }
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
