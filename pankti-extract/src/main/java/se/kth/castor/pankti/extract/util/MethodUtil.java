package se.kth.castor.pankti.extract.util;

import se.kth.castor.pankti.extract.logging.CustomLogger;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
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
     * Finds the number of nested invocations made on parameters of a target method
     * or fields of the declaring type of the target method
     *
     * @param method The target method in which nested invocations are to be found
     * @return invocationsOnFieldsOrParameters A list of nested invocations made on
     * fields or parameters
     */
    public static List<CtInvocation<?>> getNumberOfNestedInvocations(final CtMethod<?> method) {
        List<String> methodParameters = method.getParameters().stream()
                .filter(p -> !p.getType().isPrimitive())
                .map(CtNamedElement::getSimpleName)
                .collect(Collectors.toList());
        List<String> fieldNames = method.getDeclaringType().getFields().stream()
                .filter(f -> !f.getType().isPrimitive())
                .map(CtNamedElement::getSimpleName).collect(Collectors.toList());
        List<CtInvocation<?>> invocationsWithinMethod = method.getElements(new TypeFilter<>(CtInvocation.class));
        List<CtInvocation<?>> invocationsOnFieldsOrParameters = invocationsWithinMethod.stream()
                .filter(i -> i.getTarget() != null)
                .filter(i -> methodParameters.contains(i.getTarget().toString()) |
                        (fieldNames.contains(i.getTarget().toString())))
                .filter(i -> i.getTarget().getElements(new TypeFilter<>(CtLocalVariable.class)).size() == 0)
                .collect(Collectors.toList());
        return invocationsOnFieldsOrParameters;
    }

    /**
     * Checks that the invocation target for a nested method invocation is a field
     * defined in the declaring type of the method.
     *
     * @param method     The method with the nested method invocation
     * @param invocation The nested method invocation
     * @return true if the invocation target is a non-final, non-static field in the declaring type of the method
     */
    private static boolean isInvocationTargetAField(final CtMethod<?> method,
                                                    final CtInvocation<?> invocation) {
        CtField<?> field = method.getDeclaringType().getField(String.valueOf(invocation.getTarget()));
        // Invocation target is a field and not a local variable
        return field != null &&
                invocation.getTarget().getElements(new TypeFilter<>(CtFieldReferenceImpl.class)).size() == 1;
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
     * Finds nested method calls made on fields, within a method under test that are mockable.
     * <p>
     * For a nested method call to be mockable,
     * it should be invoked on a field in the declaring type of the method.
     *
     * @param outerMethod The method in which to find nested method invocations
     * @return Nested method invocations that meet all criteria for mocking
     */
    static List<CtInvocation<?>> findNestedMethodCallsOnFields(final CtMethod<?> outerMethod) {
        List<CtInvocation<?>> invocationList = outerMethod.getElements(new TypeFilter<>(CtInvocation.class));
        return invocationList.stream()
                .filter(invocation -> isNestedInvocationOnFieldMockable(outerMethod, invocation))
                .filter(invocation -> invocation.getExecutable().getType().isPrimitive())
                .filter(MethodUtil::areNestedInvocationParamsPrimitive)
                .collect(Collectors.toList());
    }

    private static boolean isExecutableEqualsOrHashCode(CtExecutableReference<?> executable) {
        return (executable.getSimpleName().equals("equals") ||
                executable.getSimpleName().equals("hashCode"));

    }

    /**
     * Instrumentation fails for java.lang.String and java.util.Collection,
     * so we exclude them
     *
     * @param executable
     * @return true if executable declaring type is String or Collection
     */
    private static boolean isExecutableDeclaringTypeStringOrCollection(CtExecutableReference<?> executable) {
        CtTypeReference<?> executableDeclaringType = executable.getDeclaringType();
        if (executableDeclaringType == null || executable.getType() == null)
            return true;
        return executableDeclaringType.getQualifiedName().equals("java.lang.String") ||
                executableDeclaringType.getQualifiedName().equals("java.util.Collection");

    }

    private static boolean isMethodDeclaringTypeSameAsExecutableDeclaringType(final CtType<?> methodDeclaringType,
                                                                              final CtTypeReference<?> executableDeclaringType) {
        return methodDeclaringType.getQualifiedName().equals(executableDeclaringType.getQualifiedName());
    }

    /**
     * Finds the invocations nested within a non-final, non-private, and non-abstract
     * target method, that are made on a parameter object.
     * The declaring type of the parameter should not be final or static.
     *
     * @param method The target method
     * @return A set of nested invocations made on parameters of the target method
     */
    public static Set<NestedTarget> getMockableInvocationsOnParameters(final CtMethod<?> method) {
        Set<NestedTarget> nestedTargets = new LinkedHashSet<>();
        List<CtParameter<?>> parameters = method.getParameters().stream()
                .filter(p -> !p.getType().isPrimitive())
                .collect(Collectors.toList());
        List<CtInvocation> invocations = method.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(ctInvocation -> ctInvocation.getTarget() != null)
                .filter(MethodUtil::areNestedInvocationParamsPrimitive)
                .collect(Collectors.toList());
        for (CtParameter<?> parameter : parameters) {
            String parameterFQN = parameter.getType().getQualifiedName();
            String parameterName = parameter.getSimpleName();
            for (CtInvocation<?> invocation : invocations) {
                // If invocation is called on parameter and parameter declaring type is different from method's
                if (Objects.requireNonNull(invocation.getTarget()).toString().equals(parameterName) &
                        !parameterFQN.equals(method.getDeclaringType().getQualifiedName())) {
                    CtExecutableReference<?> executable = getExecutable(invocation);
                    if (!isExecutableDeclaringTypeStringOrCollection(executable)
                            & executable.getType() != null) {
                        if (!isExecutableEqualsOrHashCode(executable) &
                                executable.getType().isPrimitive() & getExecutableLOC(executable) != 1) {
                            nestedTargets.add(new NestedTarget(
                                    executable.getType().getQualifiedName(),
                                    TargetType.PARAMETER,
                                    null,
                                    getDeclaringType(executable).getQualifiedName(),
                                    executable.getSimpleName(),
                                    executable.getParameters().stream().map(CtTypeInformation::getQualifiedName).collect(Collectors.toList()).toString(),
                                    executable.getSignature()));
                        }
                    }
                }
            }
        }
        return nestedTargets;
    }

    private static boolean areNestedInvocationParamsPrimitive(CtInvocation<?> invocation) {
        return invocation.getExecutable().getParameters().stream().allMatch(CtTypeInformation::isPrimitive);
    }

    private static boolean isNestedInvocationOnFieldMockable(final CtMethod<?> method,
                                                             final CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = getExecutable(invocation);
        if (isExecutableDeclaringTypeStringOrCollection(executable))
            return false;
        CtTypeReference<?> executableDeclaringType = getDeclaringType(executable);
        CtType<?> methodDeclaringType = method.getDeclaringType();
        if (!isExecutableEqualsOrHashCode(executable) &
                !isMethodDeclaringTypeSameAsExecutableDeclaringType(methodDeclaringType, executableDeclaringType)) {
            return isInvocationTargetAField(method, invocation);
        }
        return false;
    }

    private static String getFieldModifier(CtMethod<?> method,
                                           CtInvocation<?> invocation) {
        String target = invocation.getTarget().toString();
        if (target.contains("this."))
            target = invocation.getTarget()
                    .getElements(new TypeFilter<>(CtFieldReferenceImpl.class)).get(0)
                    .toString();

        Set<ModifierKind> modifiers = method.getDeclaringType()
                .getField(target)
                .getModifiers();

        return modifiers.contains(ModifierKind.PRIVATE) ?
                ModifierKind.PRIVATE.toString() :
                "default";
    }

    /**
     * The same invocation signature (declaringTypeFQN.executable(params)) may be made on
     * multiple fields within the declaring type of a target method.
     * This method finds a map of such fields and their visibility for each unique nested
     * method invocation signature.
     *
     * @param nestedMethodInvocations The invocations within a target method
     * @param method                  The target method
     * @param invocationSignature     A unique invocation signature within the target method
     * @return
     */
    public static Map<String, String> getAllFieldsWithSameInvocationSignature(
            List<CtInvocation<?>> nestedMethodInvocations,
            CtMethod<?> method, String invocationSignature) {
        Map<String, String> targetFieldVisibilityMap = new LinkedHashMap<>();
        List<CtInvocation<?>> nestedInvocations = method.getElements(
                new TypeFilter<>(CtInvocation.class)
        );
        for (CtInvocation<?> nested : nestedInvocations) {
            if (nestedMethodInvocations.contains(nested)) {
                CtExecutableReference<?> executable = getExecutable(nested);
                String signature = getDeclaringType(executable).getQualifiedName()
                        + "." + executable.getSignature();
                if (signature.equals(invocationSignature)) {
                    targetFieldVisibilityMap.put(nested.getTarget().toString(),
                            getFieldModifier(method, nested));
                }
            }
        }
        return targetFieldVisibilityMap;
    }

    /**
     * Finds nested method invocations within a method that can be mocked.
     *
     * @param method The method to check for nested invocations
     * @return A set of the mockable nested invocations made on fields of the declaring type
     * of the target method, or parameters of the target method
     */
    public static Set<NestedTarget> getNestedMethodInvocationSet(final CtMethod<?> method) {
        assert !method.isAbstract();

        Set<NestedTarget> nestedTargets = new LinkedHashSet<>();

        // Get invocations on fields
        List<CtInvocation<?>> nestedMethodInvocations = findNestedMethodCallsOnFields(method);

        for (int i = 0; i < nestedMethodInvocations.size(); i++) {
            CtExecutableReference<?> executable = getExecutable(nestedMethodInvocations.get(i));
            String nestedInvocationSignature = getDeclaringType(executable).getQualifiedName() +
                    "." + executable.getSignature();
            Map<String, String> invocationFieldVisibility =
                    getAllFieldsWithSameInvocationSignature(nestedMethodInvocations,
                            method, nestedInvocationSignature);

            if (getExecutableLOC(executable) != 1) {
                nestedTargets.add(new NestedTarget(
                        executable.getType().getQualifiedName(),
                        TargetType.FIELD,
                        invocationFieldVisibility.toString(),
                        getDeclaringType(executable).getQualifiedName(),
                        executable.getSimpleName(),
                        executable.getParameters().stream().map(CtTypeInformation::getQualifiedName)
                                .collect(Collectors.toList()).toString(),
                        executable.getSignature()));
            }
        }

        // Get invocations on parameters
        Set<NestedTarget> nestedInvocationsOnParameters = getMockableInvocationsOnParameters(method);
        nestedTargets.addAll(nestedInvocationsOnParameters);
        return nestedTargets;
    }
    private static int getExecutableLOC(CtExecutableReference<?> executable) {
        int loc;
        try {
            loc = executable.getDeclaration().getBody().getStatements().size();
        } catch (Exception e) {
            loc = 0;
        }
        return loc;
    }
}
