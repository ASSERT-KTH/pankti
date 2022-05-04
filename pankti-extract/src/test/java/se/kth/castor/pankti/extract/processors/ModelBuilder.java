package se.kth.castor.pankti.extract.processors;

import se.kth.castor.pankti.extract.launchers.PanktiLauncher;
import se.kth.castor.pankti.extract.runners.PanktiMain;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class ModelBuilder {
    static PanktiMain panktiMain;
    static PanktiLauncher panktiLauncher;
    static MavenLauncher mavenLauncher;
    static CtModel testModel;

    static {
        panktiMain = new PanktiMain(Path.of("src/test/resources/jitsi-videobridge"), false);
        panktiLauncher = new PanktiLauncher();
        mavenLauncher = panktiLauncher.getMavenLauncher(panktiMain.getProjectPath().toString(),
                panktiMain.getProjectPath().getFileName().toString());
        testModel = panktiLauncher.buildSpoonModel(mavenLauncher);
    }

    public static CtModel getModel() {
        return testModel;
    }

    public static MavenLauncher getMavenLauncher() {
        return mavenLauncher;
    }

    public static PanktiLauncher getPanktiLauncher() {
        return panktiLauncher;
    }

    public static PanktiMain getPanktiMain() {
        return panktiMain;
    }

    public static CtMethod<?> findMethodByPath(final String path, final CandidateTagger candidateTagger) {
        Optional<CtMethod<?>> optionalCtMethod =
                candidateTagger
                        .getAllMethodTags()
                        .keySet()
                        .stream()
                        .filter(k -> k.getPath().toString().equals(path))
                        .findFirst();
        return optionalCtMethod.get();
    }

    public static final List<String> primitives = List.of("void", "boolean", "byte",
            "char", "double", "float", "int", "long", "short", "java.lang.String");
}
