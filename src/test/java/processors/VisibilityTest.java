package processors;

import org.junit.jupiter.api.Test;
import spoon.MavenLauncher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class VisibilityTest {

    @Test
    public void visibilityTest() {
        MavenLauncher launcher = new MavenLauncher("src/test/resources/spoon-dog", MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.buildModel();

        CtClass<Object> aClass = launcher.getFactory().Class().get("dog.VisibilityClass");
        for (CtMethod<?> method : aClass.getAllMethods()) {
            MethodProcessor visibilityProcessor = new MethodProcessor();
            visibilityProcessor.process(method);
            String name = method.getSimpleName().toLowerCase();
            if (name.contains("public")) {
                assertTrue(visibilityProcessor.publicMethods.contains(method));
            } else if (name.contains("private")) {
                assertTrue(visibilityProcessor.privateMethods.contains(method));
            } else if (name.contains("abstract")) {
                assertTrue(visibilityProcessor.abstractMethods.contains(method));
            } else if (name.contains("static")) {
                assertTrue(visibilityProcessor.staticMethods.contains(method));
            }
        }
    }
}
