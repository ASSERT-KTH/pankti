package processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;

public class MethodProcessor extends AbstractProcessor<CtMethod<?>> {
    @Override
    public void process(CtMethod<?> ctMethod) {
        if (ctMethod.getParent(CtClass.class) != null && !ctMethod.getModifiers().contains(ModifierKind.ABSTRACT) && ctMethod.getBody().getStatements().isEmpty()) {
            if (ctMethod.isPublic())
                System.out.println(ctMethod.getSimpleName() + " is a public empty method");
            else if (ctMethod.isPrivate())
                System.out.println(ctMethod.getSimpleName() + " is a private empty method");
        }
    }
}
