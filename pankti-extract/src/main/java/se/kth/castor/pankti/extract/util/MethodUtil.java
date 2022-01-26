package se.kth.castor.pankti.extract.util;

import se.kth.castor.pankti.extract.logging.CustomLogger;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.reference.CtFieldReferenceImpl;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MethodUtil {
    private static final Logger LOGGER =
            CustomLogger.log(MethodUtil.class.getName());

    /**
     * Returns the type signature representation for method parameters.
     *
     * <p>See <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/types.html#wp16432">Oracle Java SE Documentation - Type Signatures</a>
     *
     * @param paramType A parameter passed to a method
     * @return The signature representation for the parameter
     */
    public static String findMethodParamSignature(String paramType) {
        StringBuilder paramSignature = new StringBuilder();
        if (paramType.contains("["))
            paramSignature.append('[');
        paramType = paramType.replace("[", "")
                .replace("]", "");
        // For generics
        if (paramType.length() == 1)
            paramType = Object.class.getCanonicalName();
        if (paramType.equals("boolean"))
            paramSignature.append('Z');
        else if (paramType.equals("byte"))
            paramSignature.append('B');
        else if (paramType.equals("char"))
            paramSignature.append('C');
        else if (paramType.equals("short"))
            paramSignature.append('S');
        else if (paramType.equals("int"))
            paramSignature.append('I');
        else if (paramType.equals("long"))
            paramSignature.append('J');
        else if (paramType.equals("float"))
            paramSignature.append('F');
        else if (paramType.equals("double"))
            paramSignature.append('D');
        else paramSignature.append('L').append(paramType.replace('.', '/')).append(';');
        return paramSignature.toString();
    }

    /**
     * Checks that the invocation target for a nested method invocation is a field defined in the declaring type of the method.
     *
     * @param method     The method with the nested method invocation
     * @param invocation The nested method invocation
     * @return true if the invocation target is a non-final, non-static field in the declaring type of the method
     */
    private static boolean isInvocationTargetANonFinalNonStaticField(final CtMethod<?> method,
                                                                     final CtInvocation<?> invocation) {

        CtField<?> field = method.getDeclaringType().getField(String.valueOf(invocation.getTarget()));
        // Invocation target is a field and not a local variable
        if (field != null
                && invocation.getTarget().getElements(new TypeFilter<>(CtFieldReferenceImpl.class)).size() == 1) {
            return !(field.getModifiers().contains(ModifierKind.STATIC)
                    || field.getModifiers().contains(ModifierKind.FINAL));
        }
        return false;
    }

    /**
     * @param invocation The method invocation
     * @return The executable
     */
    private static CtExecutableReference<?> getExecutable(final CtInvocation<?> invocation) {
        return invocation.getExecutable();
    }

    /**
     * @param executable The executable corresponding to the method invocation
     * @return The declaring type of the executable
     */
    private static CtTypeReference<?> getDeclaringType(final CtExecutableReference<?> executable) {
        return executable.getDeclaringType();
    }

    private static boolean isExecutableNonFinalNonStatic(final CtExecutableReference<?> executable) {
        boolean isExecutableNonFinalNonStatic = false;
        try {
            isExecutableNonFinalNonStatic = !(executable.isFinal() || executable.isStatic());
        } catch (Throwable throwable) {
            LOGGER.info(String.format("Skipping executable %s because %s", executable, throwable.getMessage()));
        }
        return isExecutableNonFinalNonStatic;
    }

    /**
     * Finds invocations within a method that can be mocked.
     *
     * <p>For a nested method call to be mocked, it should be invoked on a field in the declaring type of the method.
     * <p>This field must be non-final and non-static.
     * <p>The declaring type of this field should also be non-final and non-static.
     *
     * @param method The method in which to find nested method invocations
     * @return Nested method invocations that meet all criteria for mocking
     */
    static List<CtInvocation<?>> findNestedMethodCalls(final CtMethod<?> method) {
        List<CtInvocation<?>> invocationList = method.getElements(new TypeFilter<>(CtInvocation.class));
        return invocationList.stream()
                .filter(invocation -> isNestedInvocationMockable(method, invocation))
                .collect(Collectors.toList());
    }

    private static boolean isMethodDeclaringTypeAbstract(final CtType<?> methodDeclaringType) {
        return methodDeclaringType.getModifiers().contains(ModifierKind.ABSTRACT);
    }

    /**
     * Exclude nested invocations on methods of Java library classes
     *
     * @param executableDeclaringType The declaring type of the nested invocation executable
     * @return true if the declaring type is a java.* class
     */
    private static boolean isExecutableDeclaringTypeAJavaLibraryClass(final CtTypeReference<?> executableDeclaringType) {
        return executableDeclaringType.getQualifiedName().contains("java");
    }

    private static boolean isMethodDeclaringTypeSameAsExecutableDeclaringType(final CtType<?> methodDeclaringType,
                                                                              final CtTypeReference<?> executableDeclaringType) {
        return methodDeclaringType.getQualifiedName().equals(executableDeclaringType.getQualifiedName());
    }

    /**
     * Finds the invocations nested within a target method that are made on a parameter object
     *
     * @param method The target method
     * @return A map of nested invocations made on parameters of the target method
     */
    public static Map<String, String> getMockableInvocationsOnParameters(final CtMethod<?> method) {
        Map<String, String> nestedMethodInvocationMap = new LinkedHashMap<>();
        if (method.getDeclaringType().getModifiers().contains(ModifierKind.FINAL))
            return nestedMethodInvocationMap;
        List<CtParameter<?>> parameters = method.getParameters();
        List<CtInvocation> invocations = method.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(ctInvocation -> ctInvocation.getTarget() != null)
                .collect(Collectors.toList());
        for (CtParameter<?> parameter : parameters) {
            // if parameter is not primitive
            if (!parameter.getType().isPrimitive()) {
                String parameterFQN = parameter.getType().getQualifiedName();
                String parameterName = parameter.getSimpleName();
                for (CtInvocation<?> invocation : invocations) {
                    // If invocation is called on parameter and parameter declaring type is different from method's
                    if (Objects.requireNonNull(invocation.getTarget()).toString().equals(parameterName) &
                            !parameterFQN.equals(method.getDeclaringType().getQualifiedName())) {
                        CtExecutableReference<?> executable = getExecutable(invocation);
                        if (executable.getType() != null & !isExecutableDeclaringTypeAJavaLibraryClass(executable.getDeclaringType()) &
                                !executable.getDeclaringType().getModifiers().contains(ModifierKind.FINAL) &
                                !executable.getDeclaringType().getModifiers().contains(ModifierKind.STATIC)) {
                            nestedMethodInvocationMap.put(
                                    "PARAMETER" + invocation.getPath() + ":" + executable.getType().getQualifiedName(),
                                    getDeclaringType(executable).getQualifiedName()
                                            + "." + executable.getSignature());
                        }
                    }
                }
            }
        }
        return nestedMethodInvocationMap;
    }

    private static boolean isNestedInvocationMockable(final CtMethod<?> method,
                                                      final CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = getExecutable(invocation);
        CtTypeReference<?> executableDeclaringType = getDeclaringType(executable);
        CtType<?> methodDeclaringType = method.getDeclaringType();
        if (isExecutableNonFinalNonStatic(executable)
                && !isMethodDeclaringTypeAbstract(methodDeclaringType)
                && !isExecutableDeclaringTypeAJavaLibraryClass(executableDeclaringType)
                && !isMethodDeclaringTypeSameAsExecutableDeclaringType(methodDeclaringType, executableDeclaringType)) {
            if (isInvocationTargetANonFinalNonStaticField(method, invocation)) {
                Set<ModifierKind> invocationClassModifiers =
                        executableDeclaringType.getModifiers();
                return !(invocationClassModifiers.contains(ModifierKind.FINAL)
                        || invocationClassModifiers.contains(ModifierKind.STATIC));
            }
        }
        return false;
    }

    /**
     * Finds nested method invocations within a method that can be mocked.
     *
     * @param method The method to check for nested invocations
     * @return A map with the path of nested invocations and a string of the form "declaring.type.fqn.signature"
     */
    public static Map<String, String> getNestedMethodInvocationMap(final CtMethod<?> method) {
        assert !method.isAbstract();
        List<CtInvocation<?>> nestedMethodInvocations = findNestedMethodCalls(method);
        Map<String, String> nestedMethodInvocationMap = new LinkedHashMap<>();
        for (CtInvocation<?> invocation : nestedMethodInvocations) {
            CtExecutableReference<?> executable = getExecutable(invocation);
            nestedMethodInvocationMap.put(
                    "FIELD" + invocation.getPath() + ":" + executable.getType().getQualifiedName(),
                    getDeclaringType(executable).getQualifiedName()
                            + "." + executable.getSignature());
        }
        Map<String, String> nestedInvocationsOnParameters = getMockableInvocationsOnParameters(method);
        nestedMethodInvocationMap.putAll(nestedInvocationsOnParameters);
        return nestedMethodInvocationMap;
    }

    /**
     * Checks if the declaring type of a method has a public, non-parameterized constructor.
     *
     * @param method The method
     * @return true if the declaring type has a non-parameterized constructor
     */
    public static boolean declaringTypeHasNoParamConstructor(final CtMethod<?> method) {
        return method.getDeclaringType()
                .getElements(new TypeFilter<>(CtConstructor.class))
                .stream().anyMatch(c -> c.getParameters().isEmpty() &
                        c.getModifiers().contains(ModifierKind.PUBLIC));
    }

    public static boolean declaringTypeHasConstructorThrowingExceptions(final CtType<?> declaringType) {
        List<CtConstructor<?>> constructors = declaringType.getElements(new TypeFilter<>(CtConstructor.class));
        for (CtConstructor<?> constructor : constructors) {
            if (constructor.getThrownTypes().size() > 0)
                return true;
        }
        return false;
    }

    /**
     * Finds a public parameterized constructor of the declaring type of the target method
     * such that the constructor has the least number of parameters, and
     * neither of its parameters are primitive
     *
     * @param declaringType The class in which the target method is defined
     * @return The smallest constructor or an empty string if none is found
     */
    public static String getDeclaringTypeSmallestConstructor(CtType<?> declaringType) {
        String smallestNonDefaultConstructorParams = "";
        List<CtConstructor<?>> constructors = declaringType.getElements(new TypeFilter<>(CtConstructor.class));
        List<CtConstructor<?>> publicConstructors = constructors.stream()
                .filter(ctConstructor -> !ctConstructor.hasModifier(ModifierKind.PRIVATE))
                .filter(ctConstructor -> !ctConstructor.getSignature().contains("$"))
                .collect(Collectors.toList());
        if (publicConstructors.size() == 0)
            return smallestNonDefaultConstructorParams;
        int i = 0;
        int smallestIndex = 0;
        do {
            if (publicConstructors.get(i).getParameters().size() <= publicConstructors.get(smallestIndex).getParameters().size() &
                    publicConstructors.get(i).getParameters().stream().noneMatch(ctParameter -> ctParameter.getType().isPrimitive())) {
                smallestIndex = i;
            }
            i++;
        } while (i < publicConstructors.size());
        // Disregard smallest constructor if it has any primitive params
        if (publicConstructors.get(smallestIndex).getParameters().stream().anyMatch(ctParameter -> ctParameter.getType().isPrimitive()))
            return smallestNonDefaultConstructorParams;
        smallestNonDefaultConstructorParams = publicConstructors.get(smallestIndex).getSignature()
                .replaceAll("(.+)(\\(.+\\))", "$2")
                .replaceAll("\\(", "")
                .replaceAll("\\)", "");
        return smallestNonDefaultConstructorParams;
    }
}
