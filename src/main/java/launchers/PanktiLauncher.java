package launchers;

import logging.CustomLogger;
import taggers.CandidateTagger;
import processors.FirstMethodProcessor;
import processors.MethodProcessor;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class PanktiLauncher {
    private static final Logger LOGGER = CustomLogger.log(PanktiLauncher.class.getName());

    public MavenLauncher getMavenLauncher(final String projectPath, final String projectName) {
        MavenLauncher launcher = new MavenLauncher(projectPath, MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        return launcher;
    }

    public CtModel buildSpoonModel(final MavenLauncher launcher) {
        launcher.buildModel();
        return launcher.getModel();
    }

    public int countMethods(final CtModel model) {
        int numberOfMethodsInProject = 0;
        for (CtType<?> s : model.getAllTypes()) numberOfMethodsInProject += s.getMethods().size();
        return numberOfMethodsInProject;
    }

    public Map<CtMethod<?>, Map<String, Boolean>> applyProcessor(final CtModel model) {
        FirstMethodProcessor firstMethodProcessor = new FirstMethodProcessor();
        MethodProcessor methodProcessor = new MethodProcessor();
        CandidateTagger candidateProcessor = new CandidateTagger();
        model.processWith(firstMethodProcessor);
        model.processWith(methodProcessor);

        LOGGER.info("Modifiers present in project: " +
                methodProcessor.getAllMethodModifiersInProject());
        LOGGER.info("Number of candidate pure methods: " +
                methodProcessor.getCandidateMethods().size());
        // LOGGER.info("Candidate methods to check for purity: ");
        // methodProcessor.getCandidateMethods().forEach(ctMethod -> System.out.println(ctMethod.getPath()));

        // Tag candidate methods
        Map<CtMethod<?>, Map<String, Boolean>> methodTags = new HashMap<>();
        for (CtMethod<?> method : methodProcessor.getCandidateMethods()) {
            methodTags.putAll(candidateProcessor.tagMethod(method));
        }

        return methodTags;
    }
}
