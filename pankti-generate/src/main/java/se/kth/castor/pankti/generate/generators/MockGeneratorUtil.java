package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.SerializedObject;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockGeneratorUtil {
    static Factory factory;

    private static final String MOCKITO_ARGUMENT_MATCHER_REFERENCE = "org.mockito.ArgumentMatchers";
    private static final String invocationRegex = "(.+?)(\\.[a-zA-Z0-9]+\\(.*\\))";
    private static final List<String> primitives = List.of(
            "boolean", "byte", "char", "double", "float", "int", "long", "short", "java.lang.String");

    public static CtMethod<?> cleanUpBaseMethodCloneForMocking(CtMethod<?> baseMethod, CtField<?> mockInjectField) {
        CtMethod<?> updatedBaseMethod = baseMethod.clone();
        List<CtStatement> statements = baseMethod.getBody().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).toString().contains("receivingObjectStr"))
                updatedBaseMethod.getBody().removeStatement(statements.get(i));
            if (statements.get(i).toString().contains("fileReceiving") | statements.get(i).toString().contains("classLoader"))
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

    public static CtStatementList createParamVariableAndParse(List<String> paramTypes, String nestedParams) {
        CtStatementList statements = factory.createStatementList();
        TestGeneratorUtil testGenUtil = new TestGeneratorUtil();
        CtLocalVariable<String> nestedParamsVariable = testGenUtil.addStringVariableToTestMethod(
                factory, "nestedParamObjectStr", nestedParams);
        statements.addStatement(nestedParamsVariable);
        CtStatement parseNestedParams = factory.createCodeSnippetStatement(
                String.format("Object[] nestedParamObjects = deserializeObject(%s)",
                        "nestedParamObjectStr"));
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
        } else {
            returnedObject = serializedObject.getReturnedObject();
        }
        return returnedObject;
    }

    public static CtStatementList createReturnedVariableAndParse(String returnType, String nestedReturned) {
        CtStatementList statements = factory.createStatementList();
        TestGeneratorUtil testGenUtil = new TestGeneratorUtil();
        CtLocalVariable<String> nestedReturnedVariable = testGenUtil.addStringVariableToTestMethod(
                factory, "nestedReturnedObjectStr", nestedReturned);
        statements.addStatement(nestedReturnedVariable);
        CtStatement parseNestedReturned = factory.createCodeSnippetStatement(
                String.format("%s nestedReturnedObject = deserializeObject(%s)",
                        returnType,
                        "nestedReturnedObjectStr"));
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
