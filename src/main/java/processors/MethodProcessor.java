package processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
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
        return methodBody.isPresent() && methodBody.get().getStatements().size() == 0;
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
        // System.out.println("Modifiers in " + ctMethod.getSignature() + ": " + ctMethod.getModifiers());
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

    // Find if method has invocations
    public boolean hasInvocations(CtMethod ctMethod) {
        return ctMethod.getBody().getElements(new TypeFilter<>(CtInvocation.class)).size() > 0;
    }

    // Find if method has constructor calls
    public boolean hasConstructorCalls(CtMethod ctMethod) {
        return ctMethod.getBody().getElements(new TypeFilter<>(CtConstructorCall.class)).size() > 0;
    }

    // Find if method has assignments
    public boolean hasAssignments(CtMethod ctMethod) {
        return ctMethod.getBody().getElements(new TypeFilter<>(CtAssignment.class)).size() > 0;
    }

    public Set<CtMethod> getCandidateMethods() {
        return candidateMethods;
    }

    @Override
    public void process(CtMethod<?> ctMethod) {
        Set<ModifierKind> methodModifiers = getMethodModifiers(ctMethod);

        // If method is not empty, abstract, or synchronized
        if (!(methodModifiers.contains(ModifierKind.ABSTRACT) || methodModifiers.contains(ModifierKind.SYNCHRONIZED) || isMethodEmpty(ctMethod))) {
            // and does not throw exceptions, invoke other methods, or has assignment statements, it is a candidate
            if (!(throwsExceptions(ctMethod) || hasInvocations(ctMethod) || hasAssignments(ctMethod) || hasConstructorCalls(ctMethod)))
                candidateMethods.add(ctMethod);
        }
    }
}
