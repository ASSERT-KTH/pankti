package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.SerializedObject;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

import java.util.*;

public class MockGeneratorUtil {
    static Factory factory;

    static TestGeneratorUtil testGenUtil = new TestGeneratorUtil();

    private static final String MOCKITO_ARGUMENT_MATCHER_REFERENCE = "org.mockito.ArgumentMatchers";
    private static final String invocationRegex = "(.+?)(\\.[a-zA-Z0-9]+\\(.*\\))";
    private static final List<String> primitives = List.of(
            "boolean", "byte", "char", "double", "float", "int", "long", "short", "java.lang.String");

    public static CtMethod<?> cleanUpBaseMethodCloneForMocking(CtMethod<?> baseMethod, CtField<?> mockInjectField) {
        CtMethod<?> updatedBaseMethod = baseMethod.clone();
        List<CtStatement> statements = baseMethod.getBody().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            // Add classloader variable at the top of the method, later
            if (statements.get(i).toString().contains("getClass().getClassLoader()"))
                updatedBaseMethod.getBody().removeStatement(statements.get(i));
            if (statements.get(i).toString().contains("receivingObjectStr") | statements.get(i).toString().contains("fileReceiving"))
                updatedBaseMethod.getBody().removeStatement(statements.get(i));
            if (statements.get(i).toString().contains("assert")) {
                CtStatement updatedAssertStatement = factory.createCodeSnippetStatement(
                        baseMethod.getBody().getStatements().get(i).toString().replace("receivingObject", mockInjectField.getSimpleName()));
                updatedBaseMethod.getBody().removeStatement(statements.get(i));
                updatedBaseMethod.getBody().addStatement(updatedAssertStatement);
            }
        }
        return updatedBaseMethod;
    }

    public static CtStatement delegateClassLoaderVariableCreation() {
        return testGenUtil.addClassLoaderVariableToTestMethod(factory);
    }

    public static List<String> getListOfInvocationsFromNestedMethodMap(String nestedMethodMap) {
        List<String> methodInvocations = List.of(nestedMethodMap.split(",#"));
        List<String> sanitizedInvocations = new ArrayList<>();
        for (String invocation : methodInvocations) {
            sanitizedInvocations.add(invocation.replaceAll("(.+=)(.+)", "$2")
                    .replaceAll("}", ""));
        }
        return sanitizedInvocations;
    }

    /**
     * Finds the declaring type of the method to mock from the invocation string
     *
     * @param invocation
     * @return declaring type of method to mock
     */
    public static String getDeclaringTypeToMock(String invocation) {
        return invocation.replaceAll(invocationRegex, "$1");
    }

    /**
     * Finds the method to mock from the invocation string
     *
     * @param invocation
     * @return method to mock
     */
    public static String getMockedMethodWithParams(String invocation) {
        return invocation.replaceAll(invocationRegex, "$2").replaceAll("^\\.", "");
    }

    public static String getMockedMethodName(String methodAndParams) {
        return methodAndParams.replaceAll("(.+)(\\(.*\\))", "$1");
    }

    public static List<String> getParametersOfMockedMethod(String methodAndParams) {
        return List.of(methodAndParams.replaceAll("(.+)(\\(.*\\))", "$2")
                .replace(")", "")
                .replace("(", "")
                .split(","));
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
            if (primitives.contains(param)) {
                mockitoArgumentMatchers.add(createArgumentMatcher("any" + param.substring(0, 1).toUpperCase() + param.substring(1)));
            } else if (param.equals("java.lang.String")) {
                mockitoArgumentMatchers.add(createArgumentMatcher("anyString"));
            } else {
                mockitoArgumentMatchers.add(createArgumentMatcher("any"));
            }
        }
        return mockitoArgumentMatchers;
    }

    public static boolean arePrimitive(List<String> paramOrReturnTypes) {
        for (String paramOrReturnType : paramOrReturnTypes) {
            if (!primitives.contains(paramOrReturnType))
                return false;
        }
        return true;
    }

    public static String extractParamsOfNestedInvocation(List<String> params,
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
            returnedObject = returnedObject.replaceAll("<char>(.{1})<\\/char>", "<char>'$1'</char>")
                    .replaceAll("<string>(.+)<\\/string>", "<string>\"$1\"</string>")
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
        List<String> methodInvocations = List.of(nestedMethodMap.split(",#"));
        List<String> nestedReturnTypes = new ArrayList<>();
        for (String invocation : methodInvocations) {
            nestedReturnTypes.add(invocation.replaceAll("(.+):(.+)=(.+)", "$2"));
        }
        return nestedReturnTypes;
    }

    public static Map<String, String> sanitizeNestedInvocationMap(List<String> nestedSanitizedInvocations,
                                                                  List<String> nestedReturnType) {
        Map<String, String> returnTypeInvocationMap = new HashMap<>();
        for (int i = 0; i < nestedSanitizedInvocations.size(); i++) {
            returnTypeInvocationMap.put(nestedSanitizedInvocations.get(i), nestedReturnType.get(i));
        }
        return returnTypeInvocationMap;
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
}
