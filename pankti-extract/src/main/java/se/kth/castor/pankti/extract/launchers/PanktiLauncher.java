package se.kth.castor.pankti.extract.launchers;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import se.kth.castor.pankti.extract.logging.CustomLogger;
import se.kth.castor.pankti.extract.processors.CandidateTagger;
import se.kth.castor.pankti.extract.processors.MethodProcessor;
import se.kth.castor.pankti.extract.reporter.NestedMethodAnalysis;
import se.kth.castor.pankti.extract.selector.MockableSelector;
import se.kth.castor.pankti.extract.selector.NestedTarget;
import se.kth.castor.pankti.extract.util.MethodUtil;
import spoon.JarLauncher;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class PanktiLauncher {
    private static final Logger LOGGER = CustomLogger.log(PanktiLauncher.class.getName());
    private static String projectName;
    private static int numberOfNestedInvocationsOnFieldsOrParameters = 0;
    private static String[] HEADERS =
            {"visibility", "parent-FQN", "method-name", "param-list", "return-type",
                    "param-signature", "has-mockable-invocations", "nested-invocations"};

    public void setReportGeneration(boolean generateReport) {
        MockableSelector.generateReport = generateReport;
    }

    public Launcher getLauncher(final String projectPath, final String projectName) {
        PanktiLauncher.projectName = projectName;
        LOGGER.info("Invoking launcher for source directory");
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath);
        return launcher;
    }

    public MavenLauncher getMavenLauncher(final String projectPath, final String projectName) {
        PanktiLauncher.projectName = projectName;
        LOGGER.info("Invoking launcher for Maven project");
        MavenLauncher launcher = new MavenLauncher(projectPath, MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        return launcher;
    }

    public JarLauncher getJarLauncher(final String projectPath, final String projectName) {
        PanktiLauncher.projectName = projectName;
        LOGGER.info("Invoking launcher for JAR");
        JarLauncher launcher = new JarLauncher(projectPath);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        return launcher;
    }

    public CtModel buildSpoonModel(final Launcher launcher) {
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
            candidateMethod.putMetadata("pankti-target", true);
        }
    }

    public void createCSVFile(Map<CtMethod<?>, Map<String, Boolean>> allMethodTags) throws IOException {
        List<String> paramList;
        try (FileWriter out = new FileWriter("./extracted-methods-" + projectName + ".csv");
             CSVPrinter csvPrinter = new CSVPrinter(out, CSVFormat.DEFAULT
                     .withHeader(HEADERS));
        ) {
            for (Map.Entry<CtMethod<?>, Map<String, Boolean>> entry : allMethodTags.entrySet()) {
                CtMethod<?> method = entry.getKey();
                StringBuilder paramSignature = new StringBuilder();
                paramList = new ArrayList<>();
                if (method.getParameters().size() > 0) {
                    for (CtParameter<?> parameter : method.getParameters()) {
                        String paramType = parameter.getType().getQualifiedName();
                        paramList.add(paramType);
                        paramSignature.append(MethodUtil.findMethodParamSignature(paramType));
                    }
                }
                // Find nested method invocations that can be mocked
                numberOfNestedInvocationsOnFieldsOrParameters += MockableSelector.getNumberOfNestedInvocations(method).size();
                LinkedHashSet<NestedTarget> nestedMethodInvocations = MockableSelector.getNestedMethodInvocationSet(method);
                int methodLOC = method.getBody().getStatements().size();
                boolean isMockable = !nestedMethodInvocations.isEmpty() && methodLOC > 1;
                csvPrinter.printRecord(
                        method.getVisibility(),
                        method.getParent(CtClass.class).getQualifiedName(),
                        method.getSimpleName(),
                        paramList,
                        method.getType().getQualifiedName(),
                        paramSignature.toString(),
                        isMockable,
                        nestedMethodInvocations);
            }
        }
    }

    public Set<CtMethod<?>> applyProcessor(final CtModel model, final boolean includeVoidMethods) {
        // Filter out target methods and add metadata to them
        MethodProcessor methodProcessor = new MethodProcessor(includeVoidMethods);
        model.processWith(methodProcessor);
        LOGGER.info(methodProcessor.toString());
        LOGGER.info(String.format(!includeVoidMethods ? "not %s" : "%s", "including void methods"));
        Set<CtMethod<?>> candidateMethods = methodProcessor.getCandidateMethods();
        addMetaDataToCandidateMethods(candidateMethods);

        // Tag target methods based on their properties
        CandidateTagger candidateTagger = new CandidateTagger();
        model.processWith(candidateTagger);
        LOGGER.info(candidateTagger.toString());

        Map<CtMethod<?>, Map<String, Boolean>> allMethodTags = candidateTagger.getAllMethodTags();
        try {
            createCSVFile(allMethodTags);
            if (MockableSelector.generateReport) {
                NestedMethodAnalysis.createCSVFile();
                LOGGER.info("Generated nested method analysis report ./nested-method-anlysis.csv");
            }
        } catch (IOException e) {
            LOGGER.warning(e.getMessage());
        }
        LOGGER.info("Number of nested invocations on fields or parameters: "
                + numberOfNestedInvocationsOnFieldsOrParameters);
        LOGGER.info("Output saved in ./extracted-methods-" + projectName + ".csv");
        return candidateMethods;
    }
}
