package processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;

import java.util.ArrayList;
import java.util.List;

public class FirstMethodProcessor extends AbstractProcessor<CtMethod<?>> {
    List<CtMethod> publicMethods = new ArrayList<>();
    List<CtMethod> privateMethods = new ArrayList<>();

    @Override
    public void process(CtMethod<?> ctMethod) {
        if (ctMethod.isPublic()) {
            publicMethods.add(ctMethod);
        }
        else if (ctMethod.isPrivate()) {
            privateMethods.add(ctMethod);
        }
    }

    public void getMethodLists() {
        System.out.println("Public methods: ");
        publicMethods.forEach(ctMethod -> System.out.println(ctMethod.getSimpleName()));
        System.out.println("Private methods: " );
        privateMethods.forEach(ctMethod -> System.out.println(ctMethod.getSimpleName()));
    }
}
