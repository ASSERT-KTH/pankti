package se.kth.castor.pankti.extract.util;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.path.CtPath;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.reference.CtFieldReferenceImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

public class MethodUtil {
    /**
     * Returns the type signature representation for method parameters.
     *
     * @param paramType A parameter passed to a method
     * @return The signature representation for the parameter
     */
    public static String findMethodParamSignature(final String paramType) {
        StringBuilder paramSignature = new StringBuilder();
        if (paramType.contains("["))
            paramSignature.append('[');
        if (paramType.contains("boolean"))
            paramSignature.append('Z');
        else if (paramType.contains("byte"))
            paramSignature.append('B');
        else if (paramType.contains("char"))
            paramSignature.append('C');
        else if (paramType.contains("short"))
            paramSignature.append('S');
        else if (paramType.contains("int"))
            paramSignature.append('I');
        else if (paramType.contains("long"))
            paramSignature.append('J');
        else if (paramType.contains("float"))
            paramSignature.append('F');
        else if (paramType.contains("double"))
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

    private static boolean isNestedInvocationMockable(final CtMethod<?> method,
                                                      final CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = getExecutable(invocation);
        CtTypeReference<?> declaringType = getDeclaringType(executable);
        if (!executable.isFinal()
                && !method.getDeclaringType().getModifiers().contains(ModifierKind.ABSTRACT)
                && !method.getDeclaringType().getQualifiedName().equals(
                declaringType.getQualifiedName())) {
            if (isInvocationTargetANonFinalNonStaticField(method, invocation)) {
                Set<ModifierKind> invocationClassModifiers =
                        declaringType.getModifiers();
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
    public static Map<CtPath, String> getNestedMethodInvocationMap(final CtMethod<?> method) {
        List<CtInvocation<?>> nestedMethodInvocations = findNestedMethodCalls(method);
        Map<CtPath, String> nestedMethodInvocationMap = new LinkedHashMap<>();
        for (CtInvocation<?> invocation : nestedMethodInvocations) {
            CtExecutableReference<?> executable = getExecutable(invocation);
            nestedMethodInvocationMap.put(
                    invocation.getPath(),
                    getDeclaringType(executable).getQualifiedName()
                            + "." + executable.getSignature());
        }
        return nestedMethodInvocationMap;
    }

    /**
     * Checks if the declaring type of a method has a non-parameterized constructor.
     *
     * @param method The method
     * @return true if the declaring type has a non-parameterized constructor
     */
    public static boolean declaringTypeHasNoParamConstructor(final CtMethod<?> method) {
        return method.getDeclaringType()
                .getElements(new TypeFilter<>(CtConstructor.class))
                .stream().anyMatch(c -> c.getParameters().isEmpty());
    }
}
