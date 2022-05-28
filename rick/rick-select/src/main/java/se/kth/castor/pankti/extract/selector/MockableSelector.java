package se.kth.castor.pankti.extract.selector;

import se.kth.castor.pankti.extract.reporter.MockableCategory;
import se.kth.castor.pankti.extract.reporter.NestedMethodAnalysis;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.reference.CtExecutableReferenceImpl;
import spoon.support.reflect.reference.CtFieldReferenceImpl;

import java.util.*;
import java.util.stream.Collectors;

public class MockableSelector {
    public static boolean generateReport = false;
    static NestedMethodAnalysis reportGenerator = new NestedMethodAnalysis();

    public static boolean isPrimitiveOrString(CtTypeReference<?> type) {
        return type.isPrimitive() || type.getQualifiedName().equals("java.lang.String");
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

    public static void addToReport(CtMethod<?> outerMethod, List<CtInvocation<?>> invocationsOnFields,
                                   List<CtInvocation<?>> invocationsOnParameters) {

        List<CtInvocation<?>> invocationsOnFieldsOrParamters = new ArrayList<>();
        invocationsOnFieldsOrParamters.addAll(invocationsOnFields);
        invocationsOnFieldsOrParamters.addAll(invocationsOnParameters);

        for (CtInvocation<?> invocation : invocationsOnFieldsOrParamters) {
            boolean invocationIsOnFieldOrParam = true;
            CtExecutableReference<?> executable = getExecutable(invocation);
            CtTypeReference<?> returnType = executable.getType();
            if (returnType == null & executable.getDeclaringType() != null) {
                if (executable.getDeclaringType().isInterface())
                    if (executable.getExecutableDeclaration().getType() != null)
                        returnType = executable.getExecutableDeclaration().getType();
            }
            boolean returnsPrimitiveOrString = returnType != null && isPrimitiveOrString(returnType);
            int loc = getExecutableLOC(executable);
            boolean isExecutableNonStatic = !executable.isStatic();

            MockableCategory category = findCategory(outerMethod, executable);

            reportGenerator.generateMockabilityReport(
                    outerMethod.getDeclaringType().getQualifiedName() + "." + outerMethod.getSignature(),
                    outerMethod.getBody().getStatements().size(),
                    invocation.toString(),
                    invocationIsOnFieldOrParam,
                    returnsPrimitiveOrString,
                    loc,
                    isExecutableNonStatic,
                    category);
        }
    }

    private static MockableCategory findCategory(CtMethod<?> outerMethod, CtExecutableReference<?> executable) {
        CtType<?> outerMethodDeclaringType = outerMethod.getDeclaringType();
        while (outerMethodDeclaringType.getQualifiedName().contains("$")) {
            outerMethodDeclaringType = outerMethodDeclaringType.getDeclaringType();
        }
        String mutPackage = findTopLevelPackage(outerMethodDeclaringType.getPackage());

        CtTypeReference<?> mockableDeclaringType = executable.getDeclaringType();

        // this can happen if executable is something like toString
        if (mockableDeclaringType == null)
            return MockableCategory.NA;

        while (mockableDeclaringType.getQualifiedName().contains("$")) {
            mockableDeclaringType = mockableDeclaringType.getDeclaringType();
        }

        String mockablePackage = "";

        if (mockableDeclaringType.getQualifiedName().startsWith("java"))
            mockablePackage = mockableDeclaringType.getQualifiedName();

        if (mockablePackage.isEmpty()) {
            if (mockableDeclaringType.getPackage().getDeclaration() == null) {
                mockablePackage = mockableDeclaringType.getPackage().getQualifiedName();
            } else {
                mockablePackage = findTopLevelPackage(mockableDeclaringType.getPackage().getDeclaration());
            }
        }
        if (mockablePackage.startsWith("java")) {
            return MockableCategory.STD_LIB;
        } else if (mutPackage.equals(mockablePackage)) {
            return MockableCategory.DOMAIN;
        } else {
            return MockableCategory.THIRD_PARTY;
        }
    }

    private static String findTopLevelPackage(CtPackage declaringTypePackage) {
        String packageName = declaringTypePackage.getQualifiedName();
        while (packageName.split("\\.").length != 3) {
            if (packageName.split("\\.").length == 2)
                return packageName;
            declaringTypePackage = declaringTypePackage.getDeclaringPackage();
            packageName = declaringTypePackage.getQualifiedName();
        }
        return packageName;
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
    public static List<CtInvocation<?>> findNestedMethodCallsOnFields(final CtMethod<?> outerMethod) {
        List<CtInvocation<?>> invocationList = outerMethod.getElements(new TypeFilter<>(CtInvocation.class));
        return invocationList.stream()
                .filter(invocation -> isNestedInvocationOnFieldMockable(outerMethod, invocation))
                .filter(invocation -> isPrimitiveOrString(invocation.getExecutable().getType()))
                .collect(Collectors.toList());
    }

    private static boolean isExecutableEqualsOrHashCodeOrToString(CtExecutableReference<?> executable) {
        return (executable.getSimpleName().equals("equals") ||
                executable.getSimpleName().equals("toString") ||
                (executable.getSimpleName().equals("containsKey") &
                        getDeclaringType(executable).getQualifiedName().equals("java.util.Map")) ||
                executable.getSimpleName().equals("hashCode"));
    }

    /**
     * Instrumentation fails for java.lang.String and java.util.Collection,
     * so we exclude them
     *
     * @param executableDeclaringType
     * @return true if executable declaring type is String or Collection
     */
    private static boolean isExecutableDeclaringTypeStringOrCollection(CtTypeReference<?> executableDeclaringType) {
        return executableDeclaringType.getQualifiedName().equals("java.lang.String") ||
                executableDeclaringType.getQualifiedName().toLowerCase().contains("log") ||
                executableDeclaringType.getQualifiedName().equals("java.util.Collection");

    }

    private static boolean isMethodDeclaringTypeSameAsExecutableDeclaringType(final CtType<?> methodDeclaringType,
                                                                              final CtTypeReference<?> executableDeclaringType) {
        return methodDeclaringType.getQualifiedName().equals(executableDeclaringType.getQualifiedName());
    }

    public static Map<Integer, CtParameter<?>> getMapOfNonPrimitiveParamsAndTheirIndex(CtMethod<?> method) {
        List<CtParameter<?>> mutParams = method.getParameters();
        Map<Integer, CtParameter<?>> indexParametersMap = new LinkedHashMap<>();
        for (int i = 0; i < mutParams.size(); i++) {
            if (!mutParams.get(i).getType().isPrimitive() &
                    !mutParams.get(i).getType().getQualifiedName().equals(method.getDeclaringType().getQualifiedName())) {
                indexParametersMap.put(i, mutParams.get(i));
            }
        }
        return indexParametersMap;
    }

    /**
     * Finds the invocations nested within a non-final, non-private, and non-abstract
     * target method, that are made on a parameter object.
     * The declaring type of the parameter should not be final or static.
     *
     * @param method The target method
     * @return A set of nested invocations made on parameters of the target method
     */
    public static List<CtInvocation<?>> getMockableInvocationsOnParameters(final CtMethod<?> method,
                                                                           Map<Integer, CtParameter<?>> indexParametersMap) {
        List<CtInvocation<?>> invocationsOnParams = new LinkedList<>();

        // Get invocations in MUT
        List<CtInvocation> invocations = method.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(ctInvocation -> ctInvocation.getTarget() != null)
                .collect(Collectors.toList());

        for (Map.Entry<Integer, CtParameter<?>> indexParam : indexParametersMap.entrySet()) {
            String parameterName = indexParam.getValue().getSimpleName();
            for (CtInvocation<?> invocation : invocations) {
                // If invocation is made on parameter
                if (Objects.requireNonNull(invocation.getTarget()).toString().equals(parameterName)) {
                    CtExecutableReference<?> executable = getExecutable(invocation);
                    if (!isExecutableDeclaringTypeStringOrCollection(getDeclaringType(executable))
                            & executable.getType() != null) {
                        if (!isExecutableEqualsOrHashCodeOrToString(executable) &
                                isPrimitiveOrString(executable.getType()) & getExecutableLOC(executable) != 1) {
                            invocationsOnParams.add(invocation);
                        }
                    }
                }
            }
        }
        return invocationsOnParams;
    }

    public static Set<NestedTarget> transformInvocationsOnFieldsIntoNestedTargets(List<CtInvocation<?>> invocationsOnFields,
                                                                                  CtMethod<?> method) {
        Set<NestedTarget> nestedTargets = new LinkedHashSet<>();
        for (int i = 0; i < invocationsOnFields.size(); i++) {
            CtExecutableReference<?> executable = getExecutable(invocationsOnFields.get(i));
            String nestedInvocationSignature = getDeclaringType(executable).getQualifiedName() +
                    "." + executable.getSignature();
            Map<String, String> invocationFieldVisibility =
                    getAllFieldsWithSameInvocationSignature(invocationsOnFields,
                            method, nestedInvocationSignature);

            if (getExecutableLOC(executable) != 1) {
                nestedTargets.add(new NestedTarget(
                        executable.getType().getQualifiedName(),
                        TargetType.FIELD,
                        invocationFieldVisibility.toString(),
                        null,
                        getDeclaringType(executable).getQualifiedName(),
                        executable.getSimpleName(),
                        executable.getParameters().stream().map(CtTypeInformation::getQualifiedName)
                                .collect(Collectors.toList()).toString(),
                        executable.getSignature()));
            }
        }
        return nestedTargets;
    }

    public static Set<NestedTarget> transformInvocationsOnParamsIntoNestedTargets(List<CtInvocation<?>> invocationsOnParams,
                                                                                  Map<Integer, CtParameter<?>> indexParamsMap) {
        Set<NestedTarget> nestedTargets = new LinkedHashSet<>();
        for (Map.Entry<Integer, CtParameter<?>> indexParam : indexParamsMap.entrySet()) {
            String parameterName = indexParam.getValue().getSimpleName();
            for (CtInvocation<?> invocation : invocationsOnParams) {
                if (!invocationsOnParams.get(0).getTarget().toString().equals(parameterName))
                    continue;
                CtExecutableReference<?> executable = getExecutable(invocation);
                nestedTargets.add(new NestedTarget(
                        executable.getType().getQualifiedName(),
                        TargetType.PARAMETER,
                        null,
                        indexParam.getKey(),
                        getDeclaringType(executable).getQualifiedName(),
                        executable.getSimpleName(),
                        executable.getParameters().stream().map(CtTypeInformation::getQualifiedName).collect(Collectors.toList()).toString(),
                        executable.getSignature()));
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
        CtTypeReference<?> executableDeclaringType = getDeclaringType(executable);
        if (executableDeclaringType == null || executable.getType() == null)
            return false;
        if (isExecutableDeclaringTypeStringOrCollection(executableDeclaringType))
            return false;
        CtType<?> methodDeclaringType = method.getDeclaringType();
        if (!isExecutableEqualsOrHashCodeOrToString(executable) &
                !isMethodDeclaringTypeSameAsExecutableDeclaringType(methodDeclaringType, executableDeclaringType)) {
            return isInvocationTargetAField(method, invocation);
        }
        return false;
    }

    private static boolean isInvocationTargetAnExternalParameter(final CtMethod<?> method,
                                                                 CtInvocation<?> invocation,
                                                                 List<CtParameter<?>> parameters) {
        for (CtParameter<?> parameter : parameters) {
            if (invocation.getTarget() != null) {
                if (invocation.getTarget().toString().equals(parameter.getSimpleName()) &
                        !parameter.getType().getQualifiedName().equals(method.getDeclaringType().getQualifiedName()) &
                        !isExecutableEqualsOrHashCodeOrToString(invocation.getExecutable()))
                    return true;
            }
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


    private static int getExecutableLOC(CtExecutableReference<?> executable) {
        int loc;
        try {
            loc = executable.getDeclaration().getBody().getStatements().size();
        } catch (Exception e) {
            loc = 0;
        }
        return loc;
    }

    /**
     * Finds nested method invocations within a method that can be mocked.
     *
     * @param method The method to check for nested invocations
     * @return A set of the mockable nested invocations made on fields of the declaring type
     * of the target method, or parameters of the target method
     */
    public static LinkedHashSet<NestedTarget> getNestedMethodInvocationSet(final CtMethod<?> method) {
        assert !method.isAbstract();

        LinkedHashSet<NestedTarget> nestedTargets = new LinkedHashSet<>();

        // Get invocations on fields
        List<CtInvocation<?>> nestedMethodInvocationsOnFields = findNestedMethodCallsOnFields(method);
        Set<NestedTarget> nestedTargetsOnFields = transformInvocationsOnFieldsIntoNestedTargets(nestedMethodInvocationsOnFields, method);
        nestedTargets.addAll(nestedTargetsOnFields);

        // Get invocations on parameters
        Map<Integer, CtParameter<?>> indexParametersMap = getMapOfNonPrimitiveParamsAndTheirIndex(method);
        List<CtInvocation<?>> nestedInvocationsOnParameters =
                getMockableInvocationsOnParameters(method, indexParametersMap);
        Set<NestedTarget> nestedTargetsOnParameters =
                transformInvocationsOnParamsIntoNestedTargets(nestedInvocationsOnParameters, indexParametersMap);
        nestedTargets.addAll(nestedTargetsOnParameters);

        if (generateReport)
            addToReport(method, nestedMethodInvocationsOnFields, nestedInvocationsOnParameters);

        return nestedTargets;
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
}
