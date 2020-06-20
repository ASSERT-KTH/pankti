package se.kth.castor.pankti.launchers;

import se.kth.castor.pankti.logging.CustomLogger;
import se.kth.castor.pankti.processors.CandidateTagger;
import se.kth.castor.pankti.processors.MethodProcessor;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.Map;
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

        LOGGER.info("Pure methods and tags");
        Map<CtMethod<?>, Map<String, Boolean>> allMethodTags = candidateTagger.getAllMethodTags();
        allMethodTags.forEach((method, tags) -> System.out.println(
                "Path: " + method.getPath() + "\n" +
                        "Return type: " + method.getType() + "\n" +
                        "Tags: " + tags));

        // Instrument pure methods

        // LOGGER.info("Modifiers present in project: " + methodProcessor.getAllMethodModifiersInProject());
        // LOGGER.info("Candidate methods to check for purity: ");
        // methodProcessor.getCandidateMethods().forEach(ctMethod -> System.out.println(ctMethod.getPath()));
        return candidateMethods;
    }
}
