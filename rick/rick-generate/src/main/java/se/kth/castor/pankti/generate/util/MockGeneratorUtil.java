package se.kth.castor.pankti.generate.util;

import se.kth.castor.pankti.generate.data.SerializedObject;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.*;
import java.util.stream.Collectors;

public class MockGeneratorUtil {
    public static Factory factory;

    static TestGeneratorUtil testGenUtil = new TestGeneratorUtil();

    private static final String MOCKITO_ARGUMENT_MATCHER_REFERENCE = "org.mockito.ArgumentMatchers";
    private static final String JUNIT_JUPITER_DISPLAYNAME_REFERENCE = "org.junit.jupiter.api.DisplayName";
    private static final String invocationRegex = "(.+?)(\\.[a-zA-Z0-9]+\\(.*\\))";
    private static final List<String> primitives = List.of(
            "boolean", "byte", "char", "double", "float", "int", "long", "short", "java.lang.String");

    public static CtTypeReference<?> findOrCreateTypeReference(String type) {
        CtTypeReference<?> typeToFind;
        try {
            typeToFind = factory.createCtTypeReference(Class.forName(type));
        } catch (ClassNotFoundException e) {
            List<CtTypeReference<?>> foundTypes = findTypeFromModel(type);
            if (foundTypes.size() > 0)
                typeToFind = foundTypes.get(0);
            else return findOrCreateTypeReference("java.lang.Object");
        }
        return typeToFind;
    }

    private static String findInString(String startString, String endString, String fromString) {
        int start = fromString.indexOf(startString);
        int end = fromString.indexOf(endString);
        String result = fromString.substring(start, end).replace(startString, "");
        return result;
    }

    public static int findIndexOfStatementWithInvocationOnReceivingObject(CtMethod<?> testMethod) {
        int index = -1;
        List<CtStatement> statements = testMethod.getBody().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).toString().contains("receivingObject.")) {
                index = i;
                break;
            }
        }
        return index;
    }

    public static int findIndexOfStatementWithAssertionOrVerification(CtMethod<?> testMethod) {
        int index = -1;
        List<CtStatement> statements = testMethod.getBody().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).toString().contains("assertEquals") ||
                    statements.get(i).toString().contains("InOrder") ||
                    statements.get(i).toString().contains(".verify")) {
                index = i;
                break;
            }
        }
        return index;
    }

    public static CtMethod<?> generateHelperMethodForMockFieldInjection(String helperMethodName,
                                                                        String mockFieldType,
                                                                        String mockFieldTypeSimple,
                                                                        String mockVariableName,
                                                                        String targetFieldName,
                                                                        boolean targetFieldIsPrivate,
                                                                        String receivingObjectType) {
        CtMethod<?> helperMethod = factory.createMethod();
        helperMethod.setVisibility(ModifierKind.PRIVATE);

        // Helper method return type is same as mock field type
        CtTypeReference helperReturnType = findOrCreateTypeReference(mockFieldType);
        helperMethod.setType(helperReturnType);

        // Helper method accepts parameter of type receiving object
        CtParameter<?> receivingObjectAsParameter = factory.createParameter();
        CtTypeReference helperParameterType = findOrCreateTypeReference(receivingObjectType);
        receivingObjectAsParameter.setType(helperParameterType);
        receivingObjectAsParameter.setSimpleName("receivingObject");
        helperMethod.addParameter(receivingObjectAsParameter);

        CtStatement createMockField = factory.createCodeSnippetStatement(
                String.format("%s %s = Mockito.mock(%s.class)",
                        mockFieldTypeSimple, mockVariableName, mockFieldTypeSimple));
        helperMethod.setBody(createMockField);
        helperMethod.setSimpleName(helperMethodName);
        if (!targetFieldIsPrivate) {
            CtStatement insertMockField = factory.createCodeSnippetStatement(
                    String.format("receivingObject.%s = %s",
                            targetFieldName, mockVariableName));
            helperMethod.getBody().addStatement(insertMockField);
        } else {
            helperMethod.addThrownType(factory.createCtTypeReference(Exception.class));
            CtStatement getFieldToMock = factory.createCodeSnippetStatement(
                    String.format(
                            "Field fieldToMock = receivingObject.getClass().getDeclaredField(\"%s\")",
                            targetFieldName));
            CtStatement setAccessible = factory.createCodeSnippetStatement(
                    "fieldToMock.setAccessible(true)");
            CtStatement setValue = factory.createCodeSnippetStatement(String.format(
                    "fieldToMock.set(receivingObject, %s)",
                    mockVariableName));
            helperMethod.getBody().addStatement(getFieldToMock);
            helperMethod.getBody().addStatement(setAccessible);
            helperMethod.getBody().addStatement(setValue);
        }
        CtStatement returnStatement = factory.createCodeSnippetStatement(
                String.format("return %s", mockVariableName));
        helperMethod.getBody().addStatement(returnStatement);
        return helperMethod;
    }

    /**
     * Prepare a clone of a generated test method to support testing with mocks
     *
     * @param baseMethod             The generated test method that will be updated
     * @param targetReturnsPrimitive Whether the target method returns a primitive value
     * @return The generated base method, updated to support mocks
     */
    public static CtMethod<?> cleanUpBaseMethodCloneForMocking(CtMethod<?> baseMethod,
                                                               boolean targetReturnsPrimitive,
                                                               boolean targetReturnsVoid,
                                                               Set<Integer> paramIndices,
                                                               boolean invocationMadeOnLibraryMethod) {
        CtMethod<?> updatedBaseMethod = factory.createMethod();
        baseMethod.getAnnotations().forEach(updatedBaseMethod::addAnnotation);
        updatedBaseMethod.addThrownType(factory.createCtTypeReference(Exception.class));
        updatedBaseMethod.setVisibility(ModifierKind.PUBLIC);
        updatedBaseMethod.setType(factory.createCtTypeReference(void.class));

        List<CtStatement> statements = baseMethod.getBody().getStatements();
        List<CtStatement> statementsToRetain = new ArrayList<>();

        // Remove parameter variables that are to be mocked
        List<Integer> indicesToIgnore = new ArrayList<>();
        List<Integer> deserializationStatementIndices = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            int finalI = i;
            if (statements.get(i).toString().contains(" paramObjects["))
                deserializationStatementIndices.add(i);
            if (paramIndices.stream().anyMatch(ind -> statements.get(finalI).toString().contains(
                    String.format(" paramObjects[%d]", ind))))
                indicesToIgnore.add(i);
        }

        // If there are no other parameter deserializations, remove parameters altogether
        if (deserializationStatementIndices.equals(indicesToIgnore)) {
            for (int i = 0; i < statements.size(); i++) {
                if (statements.get(i).toString().contains("String paramsObjectStr =") |
                        statements.get(i).toString().contains("Object[] paramObjects =")) {
                    indicesToIgnore.add(i);
                }
            }
        }

        // Remove unrequired statements
        CtBlock<?> methodBody = factory.createBlock();
        for (int i = 0; i < statements.size(); i++) {
            if (indicesToIgnore.contains(i)) continue;
            if (!statements.get(i).toString().contains("returnedObjectStr") &
                    !statements.get(i).toString().contains("expectedObject = deserializeObjectFrom") &
                    !statements.get(i).toString().contains("receivingPostObjectStr") &
                    !statements.get(i).toString().contains("receivingObjectPost ="))
                statementsToRetain.add(statements.get(i));
        }

        statementsToRetain.forEach(methodBody::addStatement);
        updatedBaseMethod.setBody(methodBody);

        CtStatement assertionStatement = updatedBaseMethod.getBody().getLastStatement();
        if (targetReturnsVoid) {
            // remove assertion entirely
            updatedBaseMethod.getBody().removeStatement(assertionStatement);
        } else if (!targetReturnsPrimitive | invocationMadeOnLibraryMethod) {
            // replace assertion with call receivingObject.targetMethod(params)
            CtStatement updatedForNonPrimitiveReturn = factory.createCodeSnippetStatement(
                    assertionStatement.toString().replaceAll("(.+,\\s)(receivingObject.+)", "$2")
                            .replaceFirst("\\)", ""));
            updatedBaseMethod.getBody().removeStatement(assertionStatement);
            updatedBaseMethod.getBody().addStatement(updatedForNonPrimitiveReturn);
        }
        return updatedBaseMethod;
    }

    public static List<CtStatement> refactorAssertionStatementIntoActAndAssertion(String returnTypeMUT,
                                                                                  CtStatement mutCallStatement) {
        List<CtStatement> actAndAssertStatements = new ArrayList<>();
        String action = mutCallStatement.toString().replaceAll("(.+)(receivingObject.+\\))\\)", "$2");
        actAndAssertStatements.add(factory.createCodeSnippetStatement(String.format(
                "%s actual = %s",
                returnTypeMUT, action)));
        actAndAssertStatements.add(factory.createCodeSnippetStatement(
                mutCallStatement.toString()
                        .replaceAll("(.+)(receivingObject\\..+\\))\\)", "$1actual)")
        ));
        return actAndAssertStatements;
    }

    /**
     * Generates a variable for a mocked parameter to add within the generated test method
     *
     * @param mockParameterType
     * @return
     */
    public static CtStatement generateLocalVariableForMockParameter(String mockVariableName, String mockParameterType) {
        CtStatement mockParameterVariable = factory.createCodeSnippetStatement(String.format(
                "%s %s = Mockito.mock(%s.class)",
                mockParameterType, mockVariableName, mockParameterType));
        return mockParameterVariable;
    }

    public static CtStatement updateAssertionForInvocationOnParametersBasedOnIndex(CtStatement assertionStatement,
                                                                                   List<String> paramList,
                                                                                   int paramIndex,
                                                                                   String mockFieldName) {
        CtStatement updatedAssertStatement = null;
        for (int i = 0; i < paramList.size(); i++) {
            if (i == paramIndex) {
                String paramBeingReplaced = "paramObject" + (i + 1);
                updatedAssertStatement = factory.createCodeSnippetStatement(
                        assertionStatement.toString().replace(paramBeingReplaced, mockFieldName));
                break;
            }
        }
        return updatedAssertStatement;
    }

    public static CtStatement delegateClassLoaderVariableCreation() {
        return testGenUtil.addClassLoaderVariableToTestMethod(factory);
    }

    public static List<String> getListOfInvocationsFromNestedMethodMap(String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",\\{"));
        return methodInvocations;
    }

    /**
     * Finds if nested invocation is made on a field of the declaring type of the target method,
     * or a parameter of the target method
     *
     * @param nestedMethodMap
     * @return FIELD or PARAMETER
     */
    public static List<String> getNestedInvocationTargetTypesFromNestedMethodMap(String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",\\{"));
        List<String> targetTypes = new ArrayList<>();
        for (String invocation : methodInvocations) {
            String endString = invocation.contains("PARAMETER") ?
                    ",nestedInvocationParameterIndex" :
                    ",nestedInvocationFieldName";
            String targetType = findInString(
                    "nestedInvocationTargetType=",
                    endString,
                    invocation);
            targetTypes.add(targetType);
        }
        return targetTypes;
    }

    public static List<Integer> getNestedInvocationParameterIndex(List<String> targetTypes, String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",\\{"));
        List<Integer> paramIndices = new ArrayList<>();

        for (int i = 0; i < targetTypes.size(); i++) {
            if (targetTypes.get(i).equals("PARAMETER")) {
                // {field1=visibility, field2=visibility, field3=visibility}
                String paramIndex = findInString(
                        "nestedInvocationParameterIndex='",
                        "',nestedInvocationDeclaringType",
                        methodInvocations.get(i));
                paramIndices.add(Integer.parseInt(paramIndex));
            } else {
                paramIndices.add(null);
            }
        }
        return paramIndices;

    }

    public static List<Map<String, String>> getNestedInvocationTargetFieldVisibilityMap(List<String> targetTypes, String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",\\{"));
        Map<String, String> methodInvocationTargetFields = new LinkedHashMap<>();

        for (int i = 0; i < targetTypes.size(); i++) {
            if (targetTypes.get(i).equals("FIELD")) {
                // {field1=visibility, field2=visibility, field3=visibility}
                String fields = findInString(
                        "nestedInvocationFieldName='",
                        "',nestedInvocationDeclaringType",
                        methodInvocations.get(i));
                methodInvocationTargetFields.put(methodInvocations.get(i), fields);
            } else {
                methodInvocationTargetFields.put(methodInvocations.get(i), "");
            }
        }

        List<Map<String, String>> fieldVisibilityMaps = new ArrayList<>();

        for (Map.Entry<String, String> entry : methodInvocationTargetFields.entrySet()) {
            Map<String, String> fieldVisibilityMap = new LinkedHashMap<>();
            for (String e : entry.getValue().split(",")) {
                e = e.replaceAll("}", "").replaceAll("\\{", "");
                fieldVisibilityMap.put(e.replaceAll("(.+)(=.+)", "$1"),
                        e.replaceAll("(.+)(=.+)", "$2").replace("=", ""));
                fieldVisibilityMaps.add(fieldVisibilityMap);
            }
        }

        return fieldVisibilityMaps;
    }

    /**
     * Finds the declaring type of the method to mock from the invocation string
     *
     * @param invocation
     * @return declaring type of method to mock
     */
    public static String getDeclaringTypeToMockFromInvocationString(String invocation) {
        String declaringType = findInString(
                "nestedInvocationDeclaringType='",
                "',nestedInvocationMethod",
                invocation);
        return declaringType;
    }

    /**
     * Finds a list of types from the project model, given a name
     *
     * @param typeToFind
     * @return a list of types found for the given name
     */
    public static List<CtTypeReference<?>> findTypeFromModel(final String typeToFind) {
        return factory.getModel()
                .getAllTypes()
                .stream()
                .filter(ctType -> ctType.getQualifiedName().equals(typeToFind))
                .map(CtType::getReference)
                .collect(Collectors.toList());
    }

    /**
     * Finds the method to mock from the invocation string
     *
     * @param invocation
     * @return method to mock
     */
    public static String getMockedMethodWithParamsFromInvocationString(String invocation) {
        String methodAndParams = findInString(
                "nestedInvocationSignature='",
                "'}",
                invocation);
        return methodAndParams;
    }

    public static String getMockedMethodName(String invocation) {
        String methodName = findInString(
                "nestedInvocationMethod='",
                "',nestedInvocationParams",
                invocation);
        return methodName;
    }

    public static List<String> getParametersOfMockedMethod(String invocation) {
        String params = findInString(
                "nestedInvocationParams='[",
                "]',nestedInvocationSignature",
                invocation);
        return List.of(params.split(","));
    }

    public static List<String> getInvocationMode(String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",\\{"));
        List<String> modes = new ArrayList<>();
        for (String invocation : methodInvocations) {
            String mode = findInString(
                    "nestedInvocationMode='",
                    "',nestedInvocationReturnType",
                    invocation);
            modes.add(mode);
        }
        return modes;
    }

    public static CtExecutableReference<?> createArgumentMatcher(String name) throws ClassNotFoundException {
        CtExecutableReference<?> executableReferenceForArgumentMatcher = factory.createExecutableReference();
        executableReferenceForArgumentMatcher.setSimpleName(name);
        executableReferenceForArgumentMatcher.setStatic(true);
        executableReferenceForArgumentMatcher.setDeclaringType(factory.createCtTypeReference(Class.forName(MOCKITO_ARGUMENT_MATCHER_REFERENCE)));
        return executableReferenceForArgumentMatcher;
    }

    public static List<CtExecutableReference<?>> convertParamsToMockitoArgumentMatchers(List<String> params) throws ClassNotFoundException {
        List<CtExecutableReference<?>> mockitoArgumentMatchers = new ArrayList<>();
        for (String param : params) {
            if (param.isEmpty())
                break;
            if (param.equals("java.lang.String")) {
                mockitoArgumentMatchers.add(createArgumentMatcher("anyString"));
            } else if (primitives.contains(param)) {
                mockitoArgumentMatchers.add(createArgumentMatcher("any" + param.substring(0, 1).toUpperCase() + param.substring(1)));
            } else {
                param = param.replaceAll("\\$", ".");
                mockitoArgumentMatchers.add(createArgumentMatcher("(" + param + ") " + "any"));
            }
        }
        return mockitoArgumentMatchers;
    }

    public static boolean arePrimitiveOrString(List<String> paramOrReturnTypes) {
        for (String paramOrReturnType : paramOrReturnTypes) {
            if (!primitives.contains(paramOrReturnType))
                return false;
        }
        return true;
    }

    public static String handleNonPrimitiveParamsOfNestedInvocation(List<String> paramTypes,
                                                                    SerializedObject serializedObject) {
        StringBuilder arguments = new StringBuilder();
        List<String> params = List.of(serializedObject.getParamObjects()
                .replaceAll("</?object-array/?>", "")
                .trim()
                .split("\\n"));

        for (int i = 1; i <= paramTypes.size(); i++) {
            if (primitives.contains(paramTypes.get(i - 1))) {
                arguments.append(params.get(i - 1)
                        .replaceAll("<char>(.{1})<\\/char>", "<char>'$1'</char>")
                        .replaceAll("<string>(.+)<\\/string>", "<string>\"$1\"</string>")
                        .replaceAll("<long>(.+)<\\/long>", "<long>$1L</long>")
                        .replaceAll("<float>(.+)<\\/float>", "<float>$1F</float>")
                        .replaceAll("<\\w+>(.+)<\\/\\w+>", "eq($1)")
                        .replaceAll("\\s", ""));
            } else {
                String nonPrimitive = params.get(i - 1);
                if (nonPrimitive.contains("-array")) {
                    nonPrimitive = nonPrimitive.replaceAll("-array", "[]")
                            .replaceAll("<(.+)\\/>", "($1) any()");
                } else {
                    nonPrimitive = nonPrimitive.replaceAll("<(.+)\\/>", "any($1.class)");
                }
                arguments.append(nonPrimitive);
            }
            if (i != paramTypes.size()) {
                arguments.append(", ");
            }
        }
        return arguments.toString();
    }

    public static String extractParamsOfNestedInvocation(List<String> paramTypes,
                                                         SerializedObject serializedObject) {
        StringBuilder arguments = new StringBuilder();
        List<String> primitiveParams = List.of(serializedObject.getParamObjects()
                .replaceAll("</?object-array/?>", "")
                .trim()
                .split("\\n"));

        for (int i = 1; i <= primitiveParams.size(); i++) {
            arguments.append(primitiveParams.get(i - 1)
                    .replaceAll("<char>(.{1})<\\/char>", "<char>'$1'</char>")
                    .replaceAll("<string>(.+)<\\/string>", "<string>\"$1\"</string>")
                    .replaceAll("<long>(.+)<\\/long>", "<long>$1L</long>")
                    .replaceAll("<float>(.+)<\\/float>", "<float>$1F</float>")
                    .replaceAll("<\\w+>(.+)<\\/\\w+>", "$1")
                    .replaceAll("\\s", ""));
            if (i != primitiveParams.size()) {
                arguments.append(", ");
            }
        }
        return arguments.toString();
    }

    public static CtStatementList parseNestedParamObjectFromFileOrString(List<String> paramTypes, String fileOrStringVar) {
        CtStatementList statements = factory.createStatementList();
        CtStatement parseNestedParams = factory.createCodeSnippetStatement(
                String.format("Object[] nestedParamObjects = deserializeObject(%s)",
                        fileOrStringVar));
        statements.addStatement(parseNestedParams);

        for (int i = 0; i < paramTypes.size(); i++) {
            CtStatement parseParamObject = factory.createCodeSnippetStatement(
                    String.format("%s nestedParamObject%d = (%s) nestedParamObjects[%d]",
                            paramTypes.get(i),
                            i + 1,
                            paramTypes.get(i),
                            i));
            statements.addStatement(parseParamObject);
        }
        return statements;
    }

    public static CtStatementList createParamVariableAndParse(List<String> paramTypes, String nestedParams) {
        CtStatementList statements = factory.createStatementList();
        CtLocalVariable<String> nestedParamsVariable = testGenUtil.addStringVariableToTestMethod(
                factory, "nestedParamObjectStr", nestedParams);
        statements.addStatement(nestedParamsVariable);
        CtStatementList parsingParamsStatements = parseNestedParamObjectFromFileOrString(paramTypes, "nestedParamObjectStr");
        for (CtStatement statement : parsingParamsStatements) {
            statements.addStatement(statement);
        }
        return statements;
    }

    public static CtStatementList createParamFileAndParse(List<String> paramTypes, String nestedParams, String nestedParamIdentifier) {
        CtStatementList statements = factory.createStatementList();
        String type = "nestedParams";
        String fileName = testGenUtil.createLongObjectStringFile(nestedParamIdentifier, type, nestedParams);
        CtStatement fileVariableDeclaration = testGenUtil.addFileVariableToTestMethod(factory, fileName, type);
        statements.addStatement(fileVariableDeclaration);
        CtStatementList parsingParamsStatements = parseNestedParamObjectFromFileOrString(paramTypes, "fileNestedParams");
        for (CtStatement statement : parsingParamsStatements) {
            statements.addStatement(statement);
        }
        return statements;
    }

    public static String extractReturnedValueOfNestedInvocation(SerializedObject serializedObject,
                                                                String returnType) {
        String returnedObject = serializedObject.getReturnedObject();
        if (serializedObject.getReturnedObject().equals("<null/>")) {
            return null;
        }
        if (primitives.contains(returnType)) {
            returnedObject = returnedObject
                    .replaceAll("<char>(.{1})<\\/char>", "<char>'$1'</char>")
                    .replaceAll("<string>(.+)<\\/string>", "<string>\"$1\"</string>")
                    .replaceAll("<long>(.+)<\\/long>", "<long>$1L</long>")
                    .replaceAll("<float>(.+)<\\/float>", "<float>$1F</float>")
                    .replaceAll("<\\w+>(.+)<\\/\\w+>", "$1")
                    .replaceAll("\\s", "");
        }
        return returnedObject;
    }

    public static CtStatement parseNestedReturnedObjectFromFileOrString(String returnType, String fileOrStringVar) {
        return factory.createCodeSnippetStatement(
                String.format("%s nestedReturnedObject = deserializeObject(%s)",
                        returnType,
                        fileOrStringVar));
    }

    public static CtStatementList createReturnedVariableAndParse(String returnType, String nestedReturned) {
        String fieldName = "nestedReturnedObjectStr";
        CtStatementList statements = factory.createStatementList();
        CtLocalVariable<String> nestedReturnedVariable = testGenUtil.addStringVariableToTestMethod(
                factory, fieldName, nestedReturned);
        statements.addStatement(nestedReturnedVariable);
        CtStatement parseNestedReturned = parseNestedReturnedObjectFromFileOrString(returnType, fieldName);
        statements.addStatement(parseNestedReturned);
        return statements;
    }

    public static CtStatementList createReturnedFileAndParse(String returnType,
                                                             String nestedReturned,
                                                             String nestedReturnedIdentifier) {
        CtStatementList statements = factory.createStatementList();
        String type = "nestedReturned";
        String fileName = testGenUtil.createLongObjectStringFile(nestedReturnedIdentifier, type, nestedReturned);
        CtStatement fileVariableDeclaration = testGenUtil.addFileVariableToTestMethod(factory, fileName, type);
        statements.addStatement(fileVariableDeclaration);
        CtStatement parseNestedReturned = parseNestedReturnedObjectFromFileOrString(returnType, "fileNestedReturned");
        statements.addStatement(parseNestedReturned);
        return statements;
    }

    public static List<String> getReturnTypeFromInvocationMap(String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",\\{"));
        List<String> nestedReturnTypes = new ArrayList<>();
        for (String invocation : methodInvocations) {
            String returnType = findInString(
                    "nestedInvocationReturnType='",
                    "',nestedInvocationTargetType",
                    invocation);
            nestedReturnTypes.add(returnType);
        }
        return nestedReturnTypes;
    }

    public static List<String> sanitizeNestedInvocationMap(String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",\\{"));
        List<String> sanitizedInvocations = new ArrayList<>();
        for (String invocation : methodInvocations) {
            sanitizedInvocations.add(getDeclaringTypeToMockFromInvocationString(invocation) +
                    "." + getMockedMethodWithParamsFromInvocationString(invocation));
        }
        return sanitizedInvocations;
    }

    public static String getNestedParamsArgumentForInvocations(List<String> paramTypes) {
        StringBuilder nestedParamsBuilder = new StringBuilder();
        for (int i = 1; i <= paramTypes.size(); i++) {
            nestedParamsBuilder.append(String.format("nestedParamObject%d", i));
            if (i != paramTypes.size()) {
                nestedParamsBuilder.append(", ");
            }
        }
        return nestedParamsBuilder.toString();
    }

    public static CtAnnotation<?> generateDisplayName(String category, String mutName)
            throws ClassNotFoundException {
        CtAnnotation<?> displayNameAnnotation = factory.createAnnotation(
                factory.createCtTypeReference(Class.forName(JUNIT_JUPITER_DISPLAYNAME_REFERENCE)));
        CtLiteral<String> annotationLiteral = factory.createLiteral();
        annotationLiteral.setValue(String.format("%s for MUT %s", category, mutName));
        displayNameAnnotation.addValue("value", annotationLiteral);
        return displayNameAnnotation;
    }
}
