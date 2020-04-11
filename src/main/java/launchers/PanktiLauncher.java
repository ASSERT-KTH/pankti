package launchers;

import processors.FirstMethodProcessor;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;

public class PanktiLauncher {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        // TODO: Decide project path through input
        launcher.addInputResource("/home/user/dev/spoon-dog/");

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
        model.processWith(firstMethodProcessor);
        firstMethodProcessor.getMethodLists();

        // Save model in spooned/
        launcher.prettyprint();
    }
}
