package se.kth.castor.pankti.generate;

import spoon.MavenLauncher;
import spoon.reflect.CtModel;

public class PanktiGenLauncher {
    public MavenLauncher getMavenLauncher(final String projectPath, final String projectName) {
        MavenLauncher launcher = new MavenLauncher(projectPath, MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().disableConsistencyChecks();
        System.out.println("Skip checks: " + launcher.getEnvironment().checksAreSkipped());
        launcher.getEnvironment().setCommentEnabled(false);
        return launcher;
    }

    public CtModel buildSpoonModel(final MavenLauncher launcher) {
        launcher.buildModel();
        return launcher.getModel();
    }
}
