package processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;

import java.util.ArrayList;
import java.util.List;

public class FirstMethodProcessor extends AbstractProcessor<CtMethod<?>> {
    List<String> publicMethods = new ArrayList<>();
    List<String> privateMethods = new ArrayList<>();

    @Override
    public void process(CtMethod<?> ctMethod) {
        if (ctMethod.isPublic()) {
            publicMethods.add(ctMethod.getSimpleName());
        }
        else if (ctMethod.isPrivate()) {
            privateMethods.add(ctMethod.getSimpleName());
        }
    }

    public void getMethodLists() {
        System.out.println("Public methods: " + publicMethods);
        System.out.println("Private methods: " + privateMethods);
    }
}
