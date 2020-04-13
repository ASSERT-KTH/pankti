package launchers;

import picocli.CommandLine;
import processors.FirstMethodProcessor;
import processors.MethodProcessor;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;

import java.util.Arrays;
import java.util.List;

@CommandLine.Command(name = "java -jar target/<pankti-version-jar-with-dependencies.jar> <PATH1 PATH2 ...>", description = "pankti converts application traces to tests", separator = " ")
public class PanktiLauncher {
    // Accept file paths from CLI
    @CommandLine.Parameters(arity = "0..*", paramLabel = "FILE PATHS", description = "Space-separated project paths")
    List<String> projectPaths;

    @CommandLine.Option(names = {"-h", "--help "}, usageHelp = true, description = "exit after usage help")
    private boolean usageHelpRequested;

    public void setProjectPaths(String[] projectPaths) {
        this.projectPaths = Arrays.asList(projectPaths);
    }

    public List<String> getProjectPaths() {
        return projectPaths;
    }

    public static void main(String[] args) {
        PanktiLauncher panktiLauncher = new PanktiLauncher();
        CommandLine commandLine = new CommandLine(panktiLauncher);
        commandLine.parseArgs(args);
        if (commandLine.isUsageHelpRequested() || args.length == 0) {
            commandLine.usage(System.out);
            return;
        }
        panktiLauncher.setProjectPaths(args);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        // TODO: Refactor to include all projects
        launcher.addInputResource(panktiLauncher.getProjectPaths().get(0));

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

        // Save model in spooned/
        launcher.prettyprint();
    }
}
