package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.data.SerializedObject;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public List<CtStatement> verifyMethodCallSequence(SerializedObject parentSerializedObject) throws ClassNotFoundException {
        List<Instant> sortedTimestamps = parentSerializedObject.getNestedSerializedObjects().stream()
                .map(SerializedObject::getInvocationTimestamp)
                .sorted()
                .collect(Collectors.toList());

        Map<Integer, CtStatement> verificationStatements = new LinkedHashMap<>();

        int key = 0;
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
                String declaringTypeToMock = MockGeneratorUtil.getDeclaringTypeToMock(nested.getInvocationFQN());
                CtTypeReference mockFieldType = MockGeneratorUtil.findTypeFromModel(declaringTypeToMock).get(0);
                String mockField = String.format("mock%s", mockFieldType.getSimpleName());
                String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(nested.getInvocationFQN());
                String mockedMethod = MockGeneratorUtil.getMockedMethodName(mockedMethodWithParams);
                List<String> params = MockGeneratorUtil.getParametersOfMockedMethod(mockedMethodWithParams);
                List<CtExecutableReference<?>> paramExecutables =
                        MockGeneratorUtil.convertParamsToMockitoArgumentMatchers(params);

                verificationStatements.put(key,
                        factory.createCodeSnippetStatement(
                                String.format("Mockito.verify(%s, Mockito.times(%d)).%s(%s)",
                                        mockField, times, mockedMethod,
                                        paramExecutables.toString().substring(1,
                                                paramExecutables.toString().lastIndexOf(']')))));
            }
        }
        return new ArrayList<>(verificationStatements.values());
    }
}
