package processors;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import launchers.PanktiLauncher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

public class FirstMethodProcessorTest {

    private static PanktiLauncher panktiLauncher;
    private static MavenLauncher mavenLauncher;
    private static CtModel testModel;
    private static FirstMethodProcessor firstMethodProcessor;

    @BeforeAll
    public static void setUpLauncherAndModel() throws URISyntaxException {
        firstMethodProcessor = new FirstMethodProcessor();
        // TODO: replace with a sample project path
        panktiLauncher = new PanktiLauncher(Path.of("src/test/resources/spoon-dog"), true);
        mavenLauncher = new MavenLauncher(panktiLauncher.getProjectPath().toString(),
            MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        mavenLauncher.buildModel();
        testModel = mavenLauncher.getModel();
        testModel.processWith(firstMethodProcessor);
    }

    @Test
    public void testPomPath() {
        assertEquals(panktiLauncher.getProjectPath().toString() + "/pom.xml", mavenLauncher.getPomFile().getPath(),
            "POM file must be present in project path");
    }

    @Test
    public void testNumberOfPublicMethods() {
        assertEquals(15, firstMethodProcessor.publicMethods.size(),
            "Number of public methods in test project must be 12");
    }

    @Test
    public void testNumberOfPrivateMethods() {
        assertEquals(1, firstMethodProcessor.privateMethods.size(),
            "Number of private methods in test project must be 1");
    }
}
