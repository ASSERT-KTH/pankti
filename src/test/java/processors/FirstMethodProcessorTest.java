package processors;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import launchers.PanktiLauncher;
import static org.junit.jupiter.api.Assertions.assertEquals;

import runner.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

public class FirstMethodProcessorTest {

    private static PanktiMain panktiMain;
    private static PanktiLauncher panktiLauncher;
    private static MavenLauncher mavenLauncher;
    private static CtModel testModel;
    private static FirstMethodProcessor firstMethodProcessor;

    @BeforeAll
    public static void setUpLauncherAndModel() throws URISyntaxException {
        firstMethodProcessor = new FirstMethodProcessor();
        // TODO: replace with a sample project path
        panktiMain = new PanktiMain(Path.of("src/test/resources/spoon-dog"), true);
        mavenLauncher = new MavenLauncher(panktiMain.getProjectPath().toString(),
            MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        mavenLauncher.buildModel();
        testModel = mavenLauncher.getModel();
        testModel.processWith(firstMethodProcessor);
    }

    @Test
    public void testPomPath() {
        assertEquals(panktiMain.getProjectPath().toString() + "/pom.xml", mavenLauncher.getPomFile().getPath(),
            "POM file must be present in project path");
    }

    @Test
    public void testNumberOfPublicMethods() {
        assertEquals(19, firstMethodProcessor.publicMethods.size(),
            "Number of public methods in test project must be 15");
    }

    @Test
    public void testNumberOfPrivateMethods() {
        assertEquals(3, firstMethodProcessor.privateMethods.size(),
            "Number of private methods in test project must be 1");
    }
}
