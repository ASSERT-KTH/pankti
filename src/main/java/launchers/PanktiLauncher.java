package launchers;

import picocli.CommandLine;
import processors.FirstMethodProcessor;
import processors.MethodProcessor;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;

@CommandLine.Command(name = "java -jar target/<pankti-version-jar-with-dependencies.jar>",
        description = "pankti converts application traces to tests",
        usageHelpWidth = 100)
public class PanktiLauncher {
    // Accept project path from CLI
    @CommandLine.Parameters(paramLabel = "PROJECT PATH", description = "Path to a Maven project")
    String projectPath;

    @CommandLine.Option(names = {"-h", "--help "}, usageHelp = true, description = "exit after usage help")
    private boolean usageHelpRequested;

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public static void main(String[] args) {
        PanktiLauncher panktiLauncher = new PanktiLauncher();
        CommandLine commandLine = new CommandLine(panktiLauncher);
        // Accept project path or -h only
        try {
            commandLine.parseArgs(args);
            if (commandLine.isUsageHelpRequested()) {
                commandLine.usage(System.out);
                return;
            }
        } catch (Exception e) {
            commandLine.usage(System.out);
            return;
        }

        panktiLauncher.setProjectPath(args[0]);
        System.out.println("Processing project at " + panktiLauncher.getProjectPath());

        MavenLauncher launcher = new MavenLauncher(panktiLauncher.getProjectPath(), MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        System.out.println("POM found at " + launcher.getPomFile().getPath());
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        // Build Spoon model
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // List all packages of model
        for (CtPackage p : model.getAllPackages()) {
            System.out.println("package: " + p.getQualifiedName());
        }

        // List all classes of model
        for (CtType<?> s : model.getAllTypes()) {
            System.out.println("class: " + s.getQualifiedName());
            System.out.println("methods in class: " + s.getMethods().size());

            for (CtMethod ctMethod : s.getMethods()) {
                System.out.println(ctMethod.getSimpleName());
            }
        }

        // Apply processor to model
        FirstMethodProcessor firstMethodProcessor = new FirstMethodProcessor();
        MethodProcessor methodProcessor = new MethodProcessor();
        model.processWith(firstMethodProcessor);
        firstMethodProcessor.getMethodLists();
        model.processWith(methodProcessor);
        System.out.println("Modifiers present in project: " + methodProcessor.getAllMethodModifiersInProject());
        System.out.println("Abstract methods in project: ");
        methodProcessor.getAbstractMethods().forEach(ctMethod -> System.out.println(ctMethod.getSignature()));
        System.out.println("Empty methods in project: ");
        methodProcessor.getEmptyMethods().forEach(ctMethod -> System.out.println(ctMethod.getSignature()));
        System.out.println("Methods throwing exeptions: ");
        methodProcessor.getMethodsThrowingExcepions().forEach(ctMethod -> System.out.println(ctMethod.getSignature()));
        System.out.println("Candidate methods to check for purity: ");
        methodProcessor.getCandidateMethods().forEach(ctMethod -> System.out.println(ctMethod.getPath()));

        // Save model in spooned/
        launcher.prettyprint();
    }
}
