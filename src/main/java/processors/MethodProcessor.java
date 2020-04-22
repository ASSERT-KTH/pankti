package processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtThrow;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class MethodProcessor extends AbstractProcessor<CtMethod<?>> {
    List<CtMethod> publicMethods = new ArrayList<>();
    List<CtMethod> privateMethods = new ArrayList<>();
    List<CtMethod> protectedMethods = new ArrayList<>();
    List<CtMethod> abstractMethods = new ArrayList<>();
    List<CtMethod> staticMethods = new ArrayList<>();
    List<CtMethod> synchronizedMethods = new ArrayList<>();
    List<CtMethod> methodsThrowingExcepions = new ArrayList<>();

    List<CtMethod> emptyMethods = new ArrayList<>();

    Set<ModifierKind> allMethodModifiers = new HashSet<>();

    Set<CtMethod> candidateMethods = new HashSet<>();

    public List<CtMethod> getEmptyMethods() {
        return emptyMethods;
    }

    public List<CtMethod> getAbstractMethods() {
        return abstractMethods;
    }

    public Set<ModifierKind> getAllMethodModifiersInProject() {
        return allMethodModifiers;
    }

    public List<CtMethod> getMethodsThrowingExcepions() {
        return methodsThrowingExcepions;
    }

    // Find if method has no statements
    public boolean isMethodEmpty(CtMethod ctMethod) {
        // The body of an abstract method is null
        Optional<CtBlock> methodBody = Optional.ofNullable(ctMethod.getBody());
        if (methodBody.isPresent() && methodBody.get().getStatements().size() == 0) {
            emptyMethods.add(ctMethod);
            return true;
        }
        return false;
    }

    // Find method modifiers
    public Set<ModifierKind> getMethodModifiers(CtMethod ctMethod) {
        allMethodModifiers.addAll(ctMethod.getModifiers());
        if (ctMethod.getModifiers().contains(ModifierKind.ABSTRACT)) {
            abstractMethods.add(ctMethod);
        }
        if (ctMethod.getModifiers().contains(ModifierKind.STATIC)) {
            staticMethods.add(ctMethod);
        }
        if (ctMethod.getModifiers().contains(ModifierKind.SYNCHRONIZED)) {
            synchronizedMethods.add(ctMethod);
        }
        if (ctMethod.getModifiers().contains(ModifierKind.PUBLIC)) {
            publicMethods.add(ctMethod);
        } else if (ctMethod.getModifiers().contains(ModifierKind.PRIVATE)) {
            privateMethods.add(ctMethod);
        } else if (ctMethod.getModifiers().contains(ModifierKind.PROTECTED)) {
            protectedMethods.add(ctMethod);
        }
        System.out.println("Modifiers in " + ctMethod.getSignature() + ": " + ctMethod.getModifiers());
        return ctMethod.getModifiers();
    }

    // Find if method throws exceptions
    public boolean throwsExceptions(CtMethod ctMethod) {
        if (ctMethod.getThrownTypes().size() > 0 || (ctMethod.getBody().getElements(new TypeFilter<>(CtThrow.class)).size() > 0)) {
            methodsThrowingExcepions.add(ctMethod);
            return true;
        }
        return false;
    }

    public Set<CtMethod> getCandidateMethods() {
        return candidateMethods;
    }

    @Override
    public void process(CtMethod<?> ctMethod) {
        System.out.println("Processing " + ctMethod.getSignature());

        Set<ModifierKind> methodModifiers = getMethodModifiers(ctMethod);

        // If method is not empty, abstract, or synchronized, and does not throw exeptions, it is a candidate
        if (!(methodModifiers.contains(ModifierKind.ABSTRACT) || methodModifiers.contains(ModifierKind.SYNCHRONIZED) || isMethodEmpty(ctMethod))) {
            if (!throwsExceptions(ctMethod))
                candidateMethods.add(ctMethod);
        }
    }
}
