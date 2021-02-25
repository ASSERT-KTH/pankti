package se.kth.castor.pankti.extract.util;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * @return true if the invocation target is a final or static field in the declaring type of the method
     */
    private static boolean isInvocationTargetAFinalStaticField(final CtMethod<?> method,
                                                               final CtInvocation<?> invocation) {
        CtField<?> field = method.getDeclaringType().getField(String.valueOf(invocation.getTarget()));
        if (field != null) {
            return field.getModifiers().contains(ModifierKind.STATIC)
                    || field.getModifiers().contains(ModifierKind.FINAL);
        }
        return true;
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
     * Finds methods with nested method calls that can be mocked.
     *
     * <p>For a nested method call to be mocked, it should be invoked on a field in the declaring type of the method.
     * <p>This field must be non-final and non-static.
     * <p>The declaring type of this field should also be non-final and non-static.
     *
     * @param method Method in which to find nested method invocations
     * @return Nested method invocations that meet all criteria for mocking
     */
    public static Set<String> findNestedMethodCalls(final CtMethod<?> method) {
        Set<String> methodInvocationMap = new LinkedHashSet<>();
        Optional<List<CtInvocation<?>>> invocationList =
                Optional.ofNullable(method.getElements(new TypeFilter<>(CtInvocation.class)));

        if (invocationList.isPresent()) {
            for (int i = 0; i < invocationList.get().size(); i++) {
                CtInvocation<?> thisInvocation = invocationList.get().get(i);
                CtExecutableReference<?> executable = getExecutable(thisInvocation);
                CtTypeReference<?> declaringType = getDeclaringType(executable);
                if (!method.getDeclaringType().getQualifiedName().equals(
                        declaringType.getQualifiedName())) {
                    if (!isInvocationTargetAFinalStaticField(method, thisInvocation)) {
                        Set<ModifierKind> invocationClassModifiers =
                                declaringType.getModifiers();
                        if (!(invocationClassModifiers.contains(ModifierKind.FINAL)
                                || invocationClassModifiers.contains(ModifierKind.STATIC))) {
                            methodInvocationMap.add(
                                    declaringType.getQualifiedName() + "." + executable.getSignature());

                        }
                    }
                }
            }
        }
        return methodInvocationMap;
    }
}
