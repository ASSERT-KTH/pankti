package se.kth.castor.pankti.extract.selector;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.kth.castor.pankti.extract.launchers.PanktiLauncher;
import se.kth.castor.pankti.extract.processors.CandidateTagger;
import se.kth.castor.pankti.extract.processors.MethodProcessor;
import se.kth.castor.pankti.extract.processors.ModelBuilder;
import se.kth.castor.pankti.extract.runners.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;

import java.net.URISyntaxException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MockableSelectorTest {
    static PanktiMain panktiMain;
    static PanktiLauncher panktiLauncher;
    static MavenLauncher mavenLauncher;
    static CtModel testModel;
    static MethodProcessor methodProcessor;
    static CandidateTagger candidateTagger;
    static final String methodPath1 = "#subPackage[name=org]#subPackage[name=jitsi]" +
            "#subPackage[name=videobridge]#subPackage[name=eventadmin]" +
            "#subPackage[name=callstats]#containedType[name=Activator]" +
            "#method[signature=stop(org.osgi.framework.BundleContext)]";

    static final String methodPath2 = "#subPackage[name=org]#subPackage[name=jitsi]" +
            "#subPackage[name=videobridge]#subPackage[name=shim]" +
            "#containedType[name=ChannelShim]#method[signature=setSources(java.util.List)]";

    static final String methodPath3 = "#subPackage[name=org]#subPackage[name=jitsi]" +
            "#subPackage[name=videobridge]#containedType[name=DtlsTransport]" +
            "#method[signature=startConnectivityEstablishment(org.jitsi.xmpp.extensions.jingle.IceUdpTransportPacketExtension)]";

    static final String methodPath4 = "#subPackage[name=org]#subPackage[name=jitsi]" +
            "#subPackage[name=videobridge]#containedType[name=IceTransport]" +
            "#method[signature=startConnectivityEstablishment(org.jitsi.xmpp.extensions.jingle.IceUdpTransportPacketExtension)]";


    @BeforeAll
    public static void setUpLauncherAndModel() throws URISyntaxException {
        methodProcessor = new MethodProcessor(true);
        panktiMain = ModelBuilder.getPanktiMain();
        panktiLauncher = ModelBuilder.getPanktiLauncher();
        mavenLauncher = ModelBuilder.getMavenLauncher();
        testModel = ModelBuilder.getModel();
        testModel.processWith(methodProcessor);
        panktiLauncher.addMetaDataToCandidateMethods(methodProcessor.getCandidateMethods());
        candidateTagger = new CandidateTagger();
        testModel.processWith(candidateTagger);
    }

    private static CtMethod<?> getMUT(String methodPath) {
        return ModelBuilder.findMethodByPath(methodPath, candidateTagger);
    }

    @Test
    @DisplayName("The correct number of mockable methods should be found within an MUT")
    public void checkThatTheCorrectNumberOfMockableMethodsAreFound() {
        CtMethod<?> mut1 = getMUT(methodPath1);
        Set<NestedTarget> nestedTargetSetForMUT1 = MockableSelector.getNestedMethodInvocationSet(mut1);
        assertEquals(2, nestedTargetSetForMUT1.size());
        CtMethod<?> mut2 = getMUT(methodPath2);
        Set<NestedTarget> nestedTargetSetForMUT2 = MockableSelector.getNestedMethodInvocationSet(mut2);
        assertEquals(2, nestedTargetSetForMUT2.size());
        CtMethod<?> mut3 = getMUT(methodPath3);
        Set<NestedTarget> nestedTargetSetForMUT3 = MockableSelector.getNestedMethodInvocationSet(mut3);
        assertEquals(4, nestedTargetSetForMUT3.size());
        CtMethod<?> mut4 = getMUT(methodPath4);
        Set<NestedTarget> nestedTargetSetForMUT4 = MockableSelector.getNestedMethodInvocationSet(mut4);
        assertEquals(10, nestedTargetSetForMUT4.size());

    }

    // verify each of these properties/criteria
    // field/param of external type
    // loc of mut and mockable
    // non-static
    // non-private
    // mockable returns a primitive or a string or void
    // mut returns anything
    // mut and mockable params can be primitive + non-primitive
    // category of mockable invocation

    @ParameterizedTest
    @ValueSource(strings = {methodPath1, methodPath2, methodPath3, methodPath4})
    @DisplayName("A nested method invocation made on an external-type field is mockable " +
            "if the method is non-static and non-private")
    public void verifyPropertiesOfMockableInvocationOnFields(String methodPath) {
        CtMethod<?> mut = getMUT(methodPath);
        List<CtInvocation<?>> invocationsOnFieldsWithinMUT = MockableSelector.findNestedMethodCallsOnFields(mut);
        for (CtInvocation<?> invocation : invocationsOnFieldsWithinMUT) {
            assertNotEquals(invocation.getExecutable().getDeclaringType().getQualifiedName(),
                    mut.getDeclaringType().getQualifiedName(),
                    "The field on which the mockable method is " +
                            "invoked should be an external type");
            assertFalse(invocation.getExecutable().isStatic(),
                    "The mockable method cannot be static");
            assertTrue(MockableSelector.isPrimitiveOrString(invocation.getType()),
                    "The mockable method returns a primitive or a String");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {methodPath1, methodPath2, methodPath3, methodPath4})
    @DisplayName("A nested method invocation made on an external-type parameter is mockable " +
            "if the method is non-static and non-private")
    public void verifyPropertiesOfMockableInvocationOnParameters(String methodPath) {
        CtMethod<?> mut = getMUT(methodPath);
        Map<Integer, CtParameter<?>> indexParamsMap = MockableSelector.getMapOfNonPrimitiveParamsAndTheirIndex(mut);
        List<CtInvocation<?>> invocationsOnParametersWithinMUT =
                MockableSelector.getMockableInvocationsOnParameters(mut, indexParamsMap);
        for (CtInvocation<?> invocationOnParam : invocationsOnParametersWithinMUT) {
            assertNotEquals(invocationOnParam.getExecutable().getDeclaringType().getQualifiedName(),
                    mut.getDeclaringType().getQualifiedName(),
                    "The parameter on which the mockable method is " +
                            "invoked should be an external type");
            assertFalse(invocationOnParam.getExecutable().isStatic(),
                    "The mockable method cannot be static");
            assertTrue(MockableSelector.isPrimitiveOrString(invocationOnParam.getType()),
                    "The mockable method returns a primitive or a String");
        }
    }

    @Test
    @DisplayName("A list of nested invocations for an MUT " +
            "should be correctly transformed into nested targets")
    public void verifyThatNestedTargetPropertiesAreCorrectlySet() {
        CtMethod<?> mut = getMUT(methodPath3);
        LinkedHashSet<NestedTarget> nestedTargets = MockableSelector.getNestedMethodInvocationSet(mut);
        List<NestedTarget> nestedTargetsAsList = new LinkedList<>(nestedTargets);
        assertEquals(4, nestedTargetsAsList.size());

        NestedTarget nestedTargetAtIndex2 = nestedTargetsAsList.get(2);
        assertEquals("org.jitsi.nlj.dtls.DtlsStack",
                nestedTargetAtIndex2.getNestedInvocationDeclaringType());
        assertEquals("setRemoteFingerprints",
                nestedTargetAtIndex2.getNestedInvocationMethod());
        assertEquals(TargetType.FIELD, nestedTargetAtIndex2.getNestedInvocationTargetType());
        assertEquals("void", nestedTargetAtIndex2.getNestedInvocationReturnType());
        assertEquals("[java.util.Map]", nestedTargetAtIndex2.getNestedInvocationParams());
        assertEquals("{dtlsStack=private}", nestedTargetAtIndex2.getNestedInvocationFieldName());
        assertEquals(InvocationMode.DOMAIN, nestedTargetAtIndex2.getMode());
        assertEquals("{nestedInvocationMode='DOMAIN', nestedInvocationReturnType='void', " +
                "nestedInvocationTargetType=FIELD, nestedInvocationFieldName='{dtlsStack=private}', " +
                "nestedInvocationDeclaringType='org.jitsi.nlj.dtls.DtlsStack', " +
                "nestedInvocationMethod='setRemoteFingerprints', nestedInvocationParams='[java.util.Map]', " +
                "nestedInvocationSignature='setRemoteFingerprints(java.util.Map)'}", nestedTargetAtIndex2.toString());

        NestedTarget nestedTargetAtIndex3 = nestedTargetsAsList.get(3);
        assertEquals("org.jitsi.xmpp.extensions.AbstractPacketExtension",
                nestedTargetAtIndex3.getNestedInvocationDeclaringType());
        assertEquals("toXML",
                nestedTargetAtIndex3.getNestedInvocationMethod());
        assertEquals(TargetType.PARAMETER, nestedTargetAtIndex3.getNestedInvocationTargetType());
        assertEquals("java.lang.String", nestedTargetAtIndex3.getNestedInvocationReturnType());
        assertEquals("[]", nestedTargetAtIndex3.getNestedInvocationParams());
        assertNull(nestedTargetAtIndex3.getNestedInvocationFieldName());
        assertEquals(InvocationMode.DOMAIN, nestedTargetAtIndex3.getMode());

        assertEquals("{nestedInvocationMode='DOMAIN', nestedInvocationReturnType='java.lang.String', " +
                "nestedInvocationTargetType=PARAMETER, nestedInvocationParameterIndex='0', " +
                "nestedInvocationDeclaringType='org.jitsi.xmpp.extensions.AbstractPacketExtension', " +
                "nestedInvocationMethod='toXML', nestedInvocationParams='[]', " +
                "nestedInvocationSignature='toXML()'}", nestedTargetAtIndex3.toString());
    }
}
