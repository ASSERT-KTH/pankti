package processors;

import org.junit.jupiter.api.Test;
import spoon.MavenLauncher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;


public class PureTest {

    @Test
    public void pureTest() {
        MavenLauncher launcher = new MavenLauncher("src/test/resources/spoon-dog", MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.buildModel();

        CtClass<Object> aClass = launcher.getFactory().Class().get("dog.PureClass");
        for (CtMethod<?> method : aClass.getAllMethods()) {
            MethodProcessor visibilityProcessor = new MethodProcessor();
            visibilityProcessor.process(method);
            String name = method.getSimpleName().toLowerCase();
            if (name.contains("nonpure")) {
                assertFalse(visibilityProcessor.abstractMethods.contains(method), method.getSimpleName() + " should be a non-pure method");
            } else if (name.contains("pure")) {
                assertTrue(visibilityProcessor.candidateMethods.contains(method), method.getSimpleName() + " should be a pure method");
            }
        }
    }
}
