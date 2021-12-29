package se.kth.castor.pankti.generate.generators;

import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

import java.util.ArrayList;
import java.util.List;

public class MockGeneratorUtil {
    static Factory factory;

    private static final String MOCKITO_ARGUMENT_MATCHER_REFERENCE = "org.mockito.ArgumentMatchers";
    private static final String invocationRegex = "(.+?)(\\.[a-zA-Z0-9]+\\(.*\\))";

    public static CtMethod<?> cleanUpBaseMethodCloneForMocking(CtMethod<?> baseMethod, CtField<?> mockInjectField) {
        CtMethod<?> updatedBaseMethod = baseMethod.clone();
        List<CtStatement> statements = baseMethod.getBody().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).toString().contains("receivingObjectStr"))
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
        List<String> primitives = List.of("boolean", "byte", "char", "double", "float", "int", "long", "short");
        for (String param : params) {
            if (param.isEmpty())
                break;
            if (primitives.contains(param)) {
                mockitoArgumentMatchers.add(createArgumentMatcher("any" + param.substring(0, 1).toUpperCase() + param.substring(1)));
            } else if (param.equals("java.lang.String")) {
                mockitoArgumentMatchers.add(createArgumentMatcher("anyString"));
            }
            else {
                mockitoArgumentMatchers.add(createArgumentMatcher("any"));
            }
        }
        return mockitoArgumentMatchers;
    }
}
