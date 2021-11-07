package se.kth.castor.pankti.extract.util;

import se.kth.castor.pankti.extract.logging.CustomLogger;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
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
    public static Map<CtPath, String> getNestedMethodInvocationMap(final CtMethod<?> method) {
        assert !method.isAbstract();
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
