package processors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.filter.ReferenceTypeFilter;
import spoon.reflect.visitor.filter.TypeFilter;

@CommandLine.Command(
        name = "methodsDescription",
        description = "Process methods"
)
public class MethodProcessor extends AbstractProcessor<CtMethod<?>> implements Callable<String> {

    List<CtMethod> publicMethods = new ArrayList<>();
    List<CtMethod> privateMethods = new ArrayList<>();
    List<CtMethod> protectedMethods = new ArrayList<>();
    List<CtMethod> abstractMethods = new ArrayList<>();
    List<CtMethod> staticMethods = new ArrayList<>();
    List<CtMethod> synchronizedMethods = new ArrayList<>();
    List<CtMethod> methodsThrowingExceptions = new ArrayList<>();
    List<CtMethod> emptyMethods = new ArrayList<>();
    Set<ModifierKind> allMethodModifiers = new HashSet<>();
    Set<CtMethod> candidateMethods = new HashSet<>();

    @Override
    public String call() {
        return toString();
    }

    public Set<ModifierKind> getAllMethodModifiersInProject() {
        return allMethodModifiers;
    }

    // Find if method / parent class is @Deprecated
    public boolean isDeprecated(CtMethod ctMethod) {
        if (ctMethod.hasAnnotation(Deprecated.class) || ctMethod.getParent().hasAnnotation(Deprecated.class))
            return true;
        return false;
    }

    // Find if parent class of method is @interface
    public boolean parentHasInterfaceAnnotation(CtMethod ctMethod) {
        ReferenceTypeFilter referenceTypeFilter = new ReferenceTypeFilter(CtAnnotationType.class);
        return referenceTypeFilter.matches(ctMethod.getParent());
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
        return ctMethod.getModifiers();
    }

    // Find if method throws exceptions
    public boolean throwsExceptions(CtMethod ctMethod) {
        if (ctMethod.getThrownTypes().size() > 0 ||
                (ctMethod.getBody().getElements(new TypeFilter<>(CtThrow.class)).size() > 0)) {
            methodsThrowingExceptions.add(ctMethod);
            return true;
        }
        return false;
    }

    // Find if method has synchronized statements / blocks
    public boolean hasSynchronizedStatements(CtMethod ctMethod) {
        return ctMethod.getBody().getElements(new TypeFilter<>(CtSynchronized.class)).size() > 0;
    }

    // Find if method has invocations
    public boolean hasInvocations(CtMethod ctMethod) {
        return ctMethod.getBody().getElements(new TypeFilter<>(CtInvocation.class)).size() > 0;
    }

    // Find if method has constructor calls
    public boolean hasConstructorCalls(CtMethod ctMethod) {
        return ctMethod.getBody().getElements(new TypeFilter<>(CtConstructorCall.class)).size() > 0;
    }

    // Find if method has assignments to / unary operations on fields
    public boolean hasFieldAssignments(CtMethod ctMethod) {
        boolean hasUnaryOperationsOnFields = false;
        boolean hasAssignmentStatementsOnFields = false;

        List<CtUnaryOperator> unaryOperators = ctMethod.getBody().getElements(new TypeFilter<>(CtUnaryOperator.class));
        if (unaryOperators.size() > 0) {
            for (CtUnaryOperator unaryOperator : unaryOperators) {
                List<CtFieldWrite> fieldsBeingUpdated = unaryOperator.getOperand().getElements(new TypeFilter<>(CtFieldWrite.class));
                if (((unaryOperator.getKind().equals(UnaryOperatorKind.PREINC)) ||
                        (unaryOperator.getKind().equals(UnaryOperatorKind.PREDEC)) ||
                        (unaryOperator.getKind().equals(UnaryOperatorKind.POSTINC)) ||
                        (unaryOperator.getKind().equals(UnaryOperatorKind.POSTDEC))) &&
                        (fieldsBeingUpdated.size() > 0)) {
                    hasUnaryOperationsOnFields = true;
                }
            }
        }
        List<CtAssignment> assignmentStatements = ctMethod.getBody().getElements(new TypeFilter<>(CtAssignment.class));
        if (assignmentStatements.size() > 0) {
            for (CtAssignment assignmentStatement : assignmentStatements) {
                if (assignmentStatement.getElements(new TypeFilter<>(CtFieldWrite.class)).size() > 0) {
                    hasAssignmentStatementsOnFields = true;
                }
            }
        }
        return hasUnaryOperationsOnFields || hasAssignmentStatementsOnFields;
    }

    public Set<CtMethod> getCandidateMethods() {
        return candidateMethods;
    }

    @Override
    public void process(CtMethod<?> ctMethod) {
        Set<ModifierKind> methodModifiers = getMethodModifiers(ctMethod);
        // If method is not empty, abstract, or synchronized, does not belong to an annotation type
        if (!(methodModifiers.contains(ModifierKind.ABSTRACT) ||
                methodModifiers.contains(ModifierKind.SYNCHRONIZED) ||
                parentHasInterfaceAnnotation(ctMethod) ||
                isMethodEmpty(ctMethod))) {
            // and is not deprecated, does not throw exceptions, invoke constructors or other methods, or assign to fields, it is a candidate
            if (!(isDeprecated(ctMethod) ||
                    throwsExceptions(ctMethod) ||
                    hasInvocations(ctMethod) ||
                    hasSynchronizedStatements(ctMethod) ||
                    hasFieldAssignments(ctMethod) ||
                    hasConstructorCalls(ctMethod))) {
                candidateMethods.add(ctMethod);
            }
        }
    }

    @Override
    public String toString() {
        return "MethodProcessor{" +
                "publicMethods=" + publicMethods +
                ", privateMethods=" + privateMethods +
                ", protectedMethods=" + protectedMethods +
                ", abstractMethods=" + abstractMethods +
                ", staticMethods=" + staticMethods +
                ", synchronizedMethods=" + synchronizedMethods +
                ", methodsThrowingExceptions=" + methodsThrowingExceptions +
                ", emptyMethods=" + emptyMethods +
                ", allMethodModifiers=" + allMethodModifiers +
                ", candidateMethods=" + candidateMethods +
                '}';
    }
}
