package runner;

import launchers.PanktiLauncher;
import logging.CustomLogger;
import picocli.CommandLine;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

@CommandLine.Command(
        name = "java -jar target/<pankti-version-jar-with-dependencies.jar>",
        description = "pankti converts application traces to tests",
        usageHelpWidth = 100)
public class PanktiMain implements Callable<Integer> {
    private static final Logger LOGGER = CustomLogger.log(PanktiMain.class.getName());

    @CommandLine.Parameters(
            paramLabel = "PATH",
            description = "Path of the Maven project")
    private Path projectPath;

    @CommandLine.Option(
            names = {"-h", "--help"},
            description = "Display help/usage.",
            usageHelp = true)
    private boolean usageHelpRequested;

    public PanktiMain() {
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public PanktiMain(final Path projectPath, final boolean help) {
        this.projectPath = projectPath;
        this.usageHelpRequested = help;
    }

    @Override
    public Integer call() {
        if (usageHelpRequested) {
            return 1;
        }

        final String path = this.projectPath.toString();
        final String name = this.projectPath.getFileName().toString();

        PanktiLauncher panktiLauncher = new PanktiLauncher();

        // Process project
        LOGGER.info("Processing project: " + name);
        MavenLauncher launcher = panktiLauncher.getMavenLauncher(path, name);
        LOGGER.info("POM found at: " + launcher.getPomFile().getPath());

        // Build Spoon model
        CtModel model = panktiLauncher.buildSpoonModel(launcher);

        // Find number of methods in project
        LOGGER.info("Total number of methods: " + panktiLauncher.countMethods(model));

        // Apply processor to model
        panktiLauncher.applyProcessor(model);

        // Save model in spooned/
        // launcher.prettyprint();

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PanktiMain()).execute(args);
        System.exit(exitCode);
    }
}
