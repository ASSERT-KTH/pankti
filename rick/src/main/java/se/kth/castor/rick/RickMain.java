package se.kth.castor.rick;

import picocli.CommandLine;
import se.kth.castor.pankti.generate.PanktiGenMain;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "java -jar target/<rick-version-jar-with-dependencies.jar>",
        description = "RICK generates test cases that use mocks",
        usageHelpWidth = 200)
public class RickMain implements Callable<Integer> {
    @CommandLine.Parameters(
            index = "0",
            paramLabel = "PATH",
            description = "Path of the Maven project")
    private Path projectPath;

    @CommandLine.Parameters(
            index = "1",
            paramLabel = "CSV_FILE",
            description = "Path to CSV file with invoked methods")
    private Path methodCSVFilePath;

    @CommandLine.Parameters(
            index = "2",
            paramLabel = "DIRECTORY_WITH_OBJECT_XML_FILES",
            description = "Path to directory containing object XML files")
    private Path objectXMLDirectoryPath;

    @CommandLine.Option(
            names = {"-h", "--help"},
            description = "Display help/usage.",
            usageHelp = true)
    private boolean usageHelpRequested;

    @Override
    public Integer call() throws Exception {
        PanktiGenMain panktiGenMain = new PanktiGenMain(
                projectPath, methodCSVFilePath, objectXMLDirectoryPath,
                true, usageHelpRequested);
        panktiGenMain.call();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RickMain()).execute(args);
        System.exit(exitCode);
    }
}
