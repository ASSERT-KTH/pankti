package processors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;

@CommandLine.Command(
    name = "methodsModifiers",
    description = "Returns the methods modifiers"
)
public class FirstMethodProcessor extends AbstractProcessor<CtMethod<?>> implements Callable<String> {

    List<CtMethod> publicMethods = new ArrayList<>();
    List<CtMethod> privateMethods = new ArrayList<>();

    @Override
    public String call() throws Exception {
        return getMethodLists();
    }

    @Override
    public void process(CtMethod<?> ctMethod) {
        if (ctMethod.isPublic()) {
            publicMethods.add(ctMethod);
        } else if (ctMethod.isPrivate()) {
            privateMethods.add(ctMethod);
        }
    }

    public String getMethodLists() {
        StringBuilder methodsNames = new StringBuilder();
        methodsNames.append("Public methods: ");
        publicMethods.forEach(ctMethod -> methodsNames.append(ctMethod.getSimpleName()));
        methodsNames.append("Private methods: ");
        privateMethods.forEach(ctMethod -> methodsNames.append(ctMethod.getSimpleName()));
        return methodsNames.toString();
    }
}
