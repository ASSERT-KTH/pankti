package launchers;

import logging.CustomLogger;
import processors.CandidateTagger;
import processors.InstrumentationProcessor;
import processors.MethodProcessor;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.Set;
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

    public void addMetaDataToCandidateMethods(Set<CtMethod<?>> candidateMethods) {
        for (CtMethod<?> candidateMethod : candidateMethods) {
            candidateMethod.putMetadata("pure", true);
        }
    }

    public Set<CtMethod<?>> applyProcessor(final CtModel model) {
        // Filter out pure methods and add metadata to them
        MethodProcessor methodProcessor = new MethodProcessor();
        model.processWith(methodProcessor);
        LOGGER.info(methodProcessor.toString());
        Set<CtMethod<?>> candidateMethods = methodProcessor.getCandidateMethods();
        addMetaDataToCandidateMethods(candidateMethods);

        // Tag pure methods based on their properties
        CandidateTagger candidateTagger = new CandidateTagger();
        model.processWith(candidateTagger);
        LOGGER.info(candidateTagger.toString());

        // Instrument pure methods
        InstrumentationProcessor instrumentationProcessor = new InstrumentationProcessor();
        model.processWith(instrumentationProcessor);
        LOGGER.info(instrumentationProcessor.toString());

        // LOGGER.info("Modifiers present in project: " + methodProcessor.getAllMethodModifiersInProject());
        // LOGGER.info("Candidate methods to check for purity: ");
        // methodProcessor.getCandidateMethods().forEach(ctMethod -> System.out.println(ctMethod.getPath()));
        return candidateMethods;
    }
}
