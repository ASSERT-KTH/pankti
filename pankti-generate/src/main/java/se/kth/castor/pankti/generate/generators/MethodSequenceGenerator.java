package se.kth.castor.pankti.generate.generators;

import org.apache.commons.compress.utils.Sets;
import se.kth.castor.pankti.generate.util.MethodInvocationUtil;
import se.kth.castor.pankti.generate.data.SerializedObject;
import se.kth.castor.pankti.generate.util.MockGeneratorUtil;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class MethodSequenceGenerator {
    private static Factory factory;

    public MethodSequenceGenerator(Factory factory) {
        MethodSequenceGenerator.factory = factory;
    }

    public CtMethod<?> cleanUpGeneratedMockMethod(CtMethod<?> baseMethod) {
        CtMethod<?> updatedBaseMethod = baseMethod.clone();
        List<CtStatement> statements = baseMethod.getBody().getStatements();
        for (CtStatement statement : statements) {
            if (statement.toString().contains("nestedParam") |
                    statement.toString().contains("nestedReturned"))
                updatedBaseMethod.getBody().removeStatement(statement);
            if ((statement.toString().contains("when")) &
                    (statement.toString().contains("thenReturn")))
                updatedBaseMethod.getBody().removeStatement(statement);
            if (statement.toString().contains("verify(mock"))
                updatedBaseMethod.getBody().removeStatement(statement);
        }
        return updatedBaseMethod;
    }

    String stringifyUUID(String uuid) {
        return uuid.replace("-", "");
    }

    // For each nestedSerializedObject arranged by timestamp we need all mocks
    public CtMethod<?> generateTestToVerifyMethodSequence(Set<CtMethod<?>> generatedTestsWithMocks,
                                                          SerializedObject parentSerializedObject) throws ClassNotFoundException {
        Set<CtMethod<?>> methodsGroupedByInvocation = new LinkedHashSet<>();
        String uuid = stringifyUUID(parentSerializedObject.getUUID());
        for (CtMethod<?> generatedTest : generatedTestsWithMocks) {
            if (generatedTest.getSimpleName().contains(uuid)) {
                methodsGroupedByInvocation.add(generatedTest);
            }
        }

        // These methods have the same UUID, correspond to the same invocation,
        // We extract all distinct statements from them
        // Issue: some distinct statements are being marked as the same so we're using toString
        CtMethod<?> newMethod = factory.createMethod();
        LinkedHashSet<String> commonStatements = new LinkedHashSet<>();
        Set<String> verificationStatementsForLibraryMethods = new LinkedHashSet<>();
        List<String> verificationStatementsForDomainMethods = new LinkedList<>();
        List<String> mockVariableNamesForDomainMethods = new LinkedList<>();

        for (CtMethod<?> method : methodsGroupedByInvocation) {
            commonStatements.addAll(method.getBody().getStatements().stream().map(Object::toString)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            if (method.getSimpleName().contains("_LIBRARY")) {
                verificationStatementsForLibraryMethods.addAll(
                        method.getBody().getStatements().stream().map(Object::toString)
                                .filter(s -> s.contains("Mockito.verify"))
                                .collect(Collectors.toCollection(LinkedHashSet::new)));
            } else {
                verificationStatementsForDomainMethods.addAll(
                        method.getBody().getStatements().stream().map(Object::toString)
                                .filter(s -> s.contains("Mockito.verify"))
                                .collect(Collectors.toList()));
                mockVariableNamesForDomainMethods.addAll(
                        method.getBody().getStatements().stream().map(Object::toString)
                                .filter(s -> s.contains("Mockito.verify"))
                                .map(s -> s.substring(s.indexOf("(") + 1, s.indexOf(",")))
                                .collect(Collectors.toList()));
            }
        }

        CtBlock<?> blockWithStatementUnion = factory.createBlock();
        // Remove stubs and verifications, add everything else
        for (String s : commonStatements) {
                if (!s.contains("Mockito.verify(") & !s.contains("Assertions.assert"))
                blockWithStatementUnion.addStatement(factory.createCodeSnippetStatement(s));
        }
        // Add assertion statements in the end after some changes
        for (String s : commonStatements) {
            if (s.contains("Assertions.assert")) {
                s = s.replaceAll("(.+)(receivingObject.+)", "$2")
                        .replaceFirst("\\)", "");
                blockWithStatementUnion.addStatement(factory.createCodeSnippetStatement(s));
            }
        }

        // Add verification statements for library invocations
        for (String s : verificationStatementsForLibraryMethods) {
            blockWithStatementUnion.addStatement(factory.createCodeSnippetStatement(s));
        }

        // Add all other verification statements
        if (mockVariableNamesForDomainMethods.size() > 0) {
            List<CtStatement> verificationStatements = verifyMethodCallSequence(parentSerializedObject, mockVariableNamesForDomainMethods);
            verificationStatements.forEach(blockWithStatementUnion::addStatement);
        }

        newMethod.setBody(blockWithStatementUnion);
        newMethod.setVisibility(ModifierKind.PUBLIC);
        newMethod.setType(factory.createCtTypeReference(void.class));
        newMethod.addThrownType(factory.createCtTypeReference(Exception.class));
        return newMethod;
    }

    public List<CtStatement> verifyMethodCallSequence(SerializedObject parentSerializedObject,
                                                      List<String> mockVariableNames) throws ClassNotFoundException {
        List<Instant> sortedTimestamps = parentSerializedObject.getNestedSerializedObjects().stream()
                .map(SerializedObject::getInvocationTimestamp)
                .sorted()
                .collect(Collectors.toList());

        Map<Integer, CtStatement> verificationStatements = new LinkedHashMap<>();

        CtStatement orderVerifier = factory.createCodeSnippetStatement(String.format(
                "InOrder orderVerifier = Mockito.inOrder(%s)",
                Sets.newHashSet(mockVariableNames).toString()
                        .replaceAll("]", "").replaceAll("\\[", "")));
        verificationStatements.put(0, orderVerifier);

        int key = 1;
        int times = 1;
        for (int i = 0; i < sortedTimestamps.size(); i++) {
            SerializedObject nested = parentSerializedObject.getNestedSerializedObjects().get(i);
            if (i > 0) {
                if (nested.getInvocationFQN().equals(
                        parentSerializedObject.getNestedSerializedObjects().get(i - 1).getInvocationFQN())) {
                    // Current FQN is the same as previous, increment times called
                    times += 1;
                } else {
                    // Reset times called, increment map key
                    times = 1;
                    key += 1;
                }
            }
            if (nested.getInvocationTimestamp().equals(sortedTimestamps.get(i))) {
                // Mockito.verify(mockField, times(n)).mockedMethod(param1, param2)
                String declaringTypeToMock = MethodInvocationUtil.getDeclaringTypeFromInvocationFQN(nested.getInvocationFQN());
                CtTypeReference mockFieldType = MockGeneratorUtil.findOrCreateTypeReference(declaringTypeToMock);
                String mockField = String.format("mock%s", mockFieldType.getSimpleName());
                String mockedMethodWithParams = MethodInvocationUtil.getMethodWithParamsFromInvocationFQN(nested.getInvocationFQN());
                String mockedMethod = MethodInvocationUtil.getMethodName(mockedMethodWithParams);
                List<String> params = MethodInvocationUtil.getMethodParams(mockedMethodWithParams);
                List<CtExecutableReference<?>> paramExecutables =
                        MockGeneratorUtil.convertParamsToMockitoArgumentMatchers(params);

                String mockitoTimes = times > 1 ? ", Mockito.times(" + times + ")" : "";
                verificationStatements.put(key,
                        factory.createCodeSnippetStatement(
                                String.format("orderVerifier.verify(%s%s).%s(%s)",
                                        mockField, mockitoTimes, mockedMethod,
                                        paramExecutables.toString().substring(1,
                                                paramExecutables.toString().lastIndexOf(']')))));
            }
        }
        return new ArrayList<>(verificationStatements.values());
    }
}
