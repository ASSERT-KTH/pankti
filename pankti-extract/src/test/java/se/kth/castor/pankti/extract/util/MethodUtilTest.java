package se.kth.castor.pankti.extract.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.extract.launchers.PanktiLauncher;
import se.kth.castor.pankti.extract.processors.CandidateTagger;
import se.kth.castor.pankti.extract.processors.MethodProcessor;
import se.kth.castor.pankti.extract.runners.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodUtilTest {
    static PanktiMain panktiMain;
    static PanktiLauncher panktiLauncher;
    static MavenLauncher mavenLauncher;
    static CtModel testModel;
    static MethodProcessor methodProcessor;
    static CandidateTagger candidateTagger;
    static List<CtMethod<?>> allMethods;

    @BeforeAll
    public static void setUpLauncherAndModel() throws URISyntaxException {
        methodProcessor = new MethodProcessor(true);
        panktiMain = new PanktiMain(Path.of("src/test/resources/jitsi-videobridge"), false);
        panktiLauncher = new PanktiLauncher();
        mavenLauncher = panktiLauncher.getMavenLauncher(panktiMain.getProjectPath().toString(),
                panktiMain.getProjectPath().getFileName().toString());
        testModel = panktiLauncher.buildSpoonModel(mavenLauncher);
        testModel.processWith(methodProcessor);
        panktiLauncher.addMetaDataToCandidateMethods(methodProcessor.getCandidateMethods());
        candidateTagger = new CandidateTagger();
        testModel.processWith(candidateTagger);
        allMethods = getListOfMethods();
    }

    private static CtMethod<?> findMethodByPath(final String path) {
        Optional<CtMethod<?>> optionalCtMethod =
                candidateTagger
                        .getAllMethodTags()
                        .keySet()
                        .stream()
                        .filter(k -> k.getPath().toString().equals(path))
                        .findFirst();
        return optionalCtMethod.get();
    }

    private static List<String> getAllExtractedMethodPathsAsString() {
        return candidateTagger
                .getAllMethodTags()
                .keySet()
                .stream()
                .map(k -> k.getPath().toString())
                .collect(Collectors.toUnmodifiableList());
    }

    private static List<CtMethod<?>> getListOfMethods() {
        List<CtMethod<?>> allMethods = new ArrayList<>();
        for (String methodPath : getAllExtractedMethodPathsAsString()) {
            allMethods.add(findMethodByPath(methodPath));
        }
        return allMethods;
    }

    // Test that the correct type signature representation is generated
    @Test
    public void testMethodParameterSignature() {
        String methodPath =
                "#subPackage[name=org]#subPackage[name=jitsi]#subPackage[name=videobridge]#containedType[name=Videobridge]" +
                        "#method[signature=createConference(org.jxmpp.jid.Jid,org.jxmpp.jid.parts.Localpart,boolean,java.lang.String)]";
        CtMethod<?> method = findMethodByPath(methodPath);
        StringBuilder paramSignature = new StringBuilder();
        for (CtParameter<?> parameter : method.getParameters()) {
            String paramType = parameter.getType().getQualifiedName();
            paramSignature.append(MethodUtil.findMethodParamSignature(paramType));
        }
        assertEquals("Lorg/jxmpp/jid/Jid;Lorg/jxmpp/jid/parts/Localpart;ZLjava/lang/String;",
                paramSignature.toString());
    }

    @Test
    public void testNoMockCandidatesFoundInMethodWithoutCandidates() {
        String methodPath =
                "#subPackage[name=org]#subPackage[name=jitsi]#subPackage[name=videobridge]#containedType[name=Conference]" +
                        "#method[signature=getDebugState(boolean,java.lang.String)]";
        CtMethod<?> method = findMethodByPath(methodPath);
        assertEquals(0, MethodUtil.getNestedMethodInvocationSet(method).size(),
                String.format("%s has no nested method invocations that may be mocked",
                        method.getSignature()));
    }

    // Test that the correct type signature for parameters is generated for corner cases
    @Test
    public void testMethodParameterSignatureForCornerCases() {
        String[] params = {"float", "org.example.floaty.McFloatFace", "short[]", "org.example.interesting.booleany.Thing[]", "E"};
        StringBuilder paramSignature = new StringBuilder();
        for (String param : params) {
            paramSignature.append(MethodUtil.findMethodParamSignature(param));
        }
        assertEquals("FLorg/example/floaty/McFloatFace;[S[Lorg/example/interesting/booleany/Thing;Ljava/lang/Object;",
                paramSignature.toString());
    }

    @Test
    public void testCorrectAmountOfMockCandidatesAreFound() {
        String methodPath1 =
                "#subPackage[name=org]#subPackage[name=jitsi]#subPackage[name=videobridge]#containedType[name=Endpoint]" +
                        "#method[signature=getDebugState()]";
        String methodPath2 =
                "#subPackage[name=org]#subPackage[name=jitsi]#subPackage[name=videobridge]#containedType[name=Conference]" +
                        "#method[signature=getTentacle()]";
        CtMethod<?> method1 = findMethodByPath(methodPath1);
        CtMethod<?> method2 = findMethodByPath(methodPath2);
        assertEquals(0, MethodUtil.getNestedMethodInvocationSet(method1).size(),
                String.format("%s has zero nested method invocations that may be mocked",
                        method1.getSignature()));
        assertEquals(0, MethodUtil.getNestedMethodInvocationSet(method2).size(),
                String.format("%s has zero nested method invocations that may be mocked",
                        method2.getSignature()));
    }

    @Test
    @Disabled("This constraint removed to allow nested invocations on Java library methods")
    public void testThatMethodsWithMockingCandidatesDoNotHaveNestedInvocationsOnJavaLibraryClassMethods() {
        for (CtMethod<?> thisMethod : allMethods) {
            List<CtInvocation<?>> methodInvocations = MethodUtil.findNestedMethodCalls(thisMethod);
            for (CtInvocation<?> thisInvocation : methodInvocations) {
                assertFalse(thisInvocation.getExecutable().getDeclaringType().getQualifiedName().contains("java"),
                        String.format("The declaring type for %s should not be a Java library class for it to be mocked",
                                thisInvocation));
            }
        }
    }

    @Test
    public void testThatMethodsWithMockingCandidatesAreNotDeclaredInAnAbstractClass() {
        for (CtMethod<?> method : allMethods) {
            if (!MethodUtil.findNestedMethodCalls(method).isEmpty()) {
                assertFalse(method.getDeclaringType().getModifiers().contains(ModifierKind.ABSTRACT),
                        String.format("The declaring type for %s should not be an abstract class to allow mock injections",
                                method.getSignature()));
            }
        }
    }

    @Test
    public void testThatMockingCandidatesAreNotDeclaredInAStaticClass() {
        for (CtMethod<?> thisMethod : allMethods) {
            List<CtInvocation<?>> methodInvocations = MethodUtil.findNestedMethodCalls(thisMethod);
            for (CtInvocation<?> thisInvocation : methodInvocations) {
                assertFalse(thisInvocation.getExecutable().getDeclaringType().getModifiers().contains(ModifierKind.STATIC),
                        String.format("The declaring type for %s should not be static for it to be mocked",
                                thisInvocation));
            }
        }
    }

    @Test
    public void testThatMockingCandidatesAreNotDeclaredInAFinalClass() {
        for (CtMethod<?> thisMethod : allMethods) {
            List<CtInvocation<?>> methodInvocations = MethodUtil.findNestedMethodCalls(thisMethod);
            for (CtInvocation<?> thisInvocation : methodInvocations) {
                assertFalse(thisInvocation.getExecutable().getDeclaringType().getModifiers().contains(ModifierKind.FINAL),
                        String.format("The declaring type for %s should not be final for it to be mocked",
                                thisInvocation));
            }
        }
    }

    @Test
    public void testThatAllInvocationExecutablesAreNonStatic() {
        for (CtMethod<?> thisMethod : allMethods) {
            List<CtInvocation<?>> methodInvocations = MethodUtil.findNestedMethodCalls(thisMethod);
            for (CtInvocation<?> thisInvocation : methodInvocations) {
                assertFalse(thisInvocation.getExecutable().isStatic(),
                        String.format("The executable for %s should not be static for it to be mocked",
                                thisInvocation));
            }
        }
    }

    @Test
    public void testThatAllInvocationExecutablesAreNonFinal() {
        for (CtMethod<?> thisMethod : allMethods) {
            List<CtInvocation<?>> methodInvocations = MethodUtil.findNestedMethodCalls(thisMethod);
            for (CtInvocation<?> thisInvocation : methodInvocations) {
                assertFalse(thisInvocation.getExecutable().isFinal(),
                        String.format("The executable for %s should not be final for it to be mocked",
                                thisInvocation));
            }
        }
    }

    @Test
    public void testThatANoParamConstructorIsFoundWhereItExists() {
        String path = "#subPackage[name=org]#subPackage[name=jitsi]#subPackage[name=videobridge]#containedType[name=Videobridge]" +
                "#method[signature=isXmppApiEnabled()]";
        CtMethod<?> method = findMethodByPath(path);
        assertTrue(MethodUtil.declaringTypeHasNoParamConstructor(method),
                String.format("%s has a non-parameterized constructor",
                        method.getDeclaringType().getQualifiedName()));
    }

    @Test
    public void testThatNoParamConstructorsAreNotFoundWhereThereArentAny() {
        String path = "#subPackage[name=org]#subPackage[name=jitsi]#subPackage[name=videobridge]#containedType[name=IceTransport]" +
                "#method[signature=isConnected()]";
        CtMethod<?> method = findMethodByPath(path);
        assertFalse(MethodUtil.declaringTypeHasNoParamConstructor(method),
                String.format("%s does not have a non-parameterized constructor",
                        method.getDeclaringType().getQualifiedName()));
    }
}
