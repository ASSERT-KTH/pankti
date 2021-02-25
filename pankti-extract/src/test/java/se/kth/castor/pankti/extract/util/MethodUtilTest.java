package se.kth.castor.pankti.extract.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.extract.launchers.PanktiLauncher;
import se.kth.castor.pankti.extract.processors.CandidateTagger;
import se.kth.castor.pankti.extract.processors.MethodProcessor;
import se.kth.castor.pankti.extract.runners.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodUtilTest {
    static PanktiMain panktiMain;
    static PanktiLauncher panktiLauncher;
    static MavenLauncher mavenLauncher;
    static CtModel testModel;
    static MethodProcessor methodProcessor;
    static CandidateTagger candidateTagger;
    static final String methodPath =
            "#subPackage[name=org]#subPackage[name=jitsi]#subPackage[name=videobridge]#containedType[name=Conference]#method[signature=getDebugState(boolean,java.lang.String)]";

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
    }

    // Test that nested method invocations that can be mocked are identified
    @Test
    public void testMockCandidatesAreMarked() {
        Optional<CtMethod<?>> optionalCtMethod =
                candidateTagger
                        .getAllMethodTags()
                        .keySet()
                        .stream()
                        .filter(k -> k.getPath().toString().equals(methodPath))
                        .findFirst();
        CtMethod<?> method = optionalCtMethod.get();
        assertEquals(2, MethodUtil.findNestedMethodCalls(method).size(),
                String.format("%s has two nested method invocations that may be mocked",
                        method.getSignature()));
    }
}
