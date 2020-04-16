package processors;

import launchers.PanktiLauncher;
import org.junit.jupiter.api.*;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

import static org.junit.jupiter.api.Assertions.*;

public class FirstMethodProcessorTest {
    static PanktiLauncher testPanktiLauncher;
    static MavenLauncher testLauncher;
    static CtModel testModel;
    static FirstMethodProcessor firstMethodProcessor = new FirstMethodProcessor();

    @BeforeAll
    public static void setUpLauncherAndModel() {
        testPanktiLauncher = new PanktiLauncher();
        // TODO: replace with a sample project path
        String[] args = new String[]{"/home/user/dev/spoon-dog"};
        testPanktiLauncher.setProjectPath(args[0]);
        testLauncher = new MavenLauncher(testPanktiLauncher.getProjectPath(), MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        testLauncher.buildModel();
        testModel = testLauncher.getModel();
        testModel.processWith(firstMethodProcessor);
    }

    @Test
    public void testPomPath() {
        assertEquals(testPanktiLauncher.getProjectPath() + "/pom.xml", testLauncher.getPomFile().getPath(),
                "POM file must be present in project path");
    }

    @Test
    public void testNumberOfPublicMethods() {
        assertEquals(12, firstMethodProcessor.publicMethods.size(),
                "Number of public methods in test project must be 12");
    }

    @Test
    public void testNumberOfPrivateMethods() {
        assertEquals(1, firstMethodProcessor.privateMethods.size(),
                "Number of private methods in test project must be 1");
    }
}
