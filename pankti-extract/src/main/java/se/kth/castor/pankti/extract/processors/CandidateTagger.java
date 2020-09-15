package se.kth.castor.pankti.extract.processors;

import se.kth.castor.pankti.extract.logging.CustomLogger;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.logging.Logger;

public class CandidateTagger extends AbstractProcessor<CtMethod<?>> {
    private static final Logger LOGGER = CustomLogger.log(CandidateTagger.class.getName());

    List<CtMethod<?>> staticMethods = new ArrayList<>();
    List<CtMethod<?>> methodsReturningAValue = new ArrayList<>();
    List<CtMethod<?>> methodsNotReturningAValue = new ArrayList<>();
    List<CtMethod<?>> methodsReturningAPrimitive = new ArrayList<>();
    List<CtMethod<?>> methodsWithParameters = new ArrayList<>();
    List<CtMethod<?>> methodsWithIfConditions = new ArrayList<>();
    List<CtMethod<?>> methodsWithConditionalOperators = new ArrayList<>();
    List<CtMethod<?>> methodsWithLoops = new ArrayList<>();
    List<CtMethod<?>> methodsWithLocalVariables = new ArrayList<>();
    List<CtMethod<?>> methodsWithSwitchStatements = new ArrayList<>();
    List<CtMethod<?>> methodsWithMultipleStatements = new ArrayList<>();
    Map<CtMethod<?>, Map<String, Boolean>> allMethodTags = new HashMap<>();

    private Map.Entry<String, Boolean> isStatic(CtMethod<?> ctMethod) {
        boolean isStatic = false;
        if (ctMethod.isStatic()) {
            staticMethods.add(ctMethod);
            isStatic = true;
        }
        return Map.entry("static", isStatic);
    }

    private Map.Entry<String, Boolean> getIfs(CtMethod<?> ctMethod) {
        boolean hasIfs = false;
        if (ctMethod.getElements(new TypeFilter<>(CtIf.class)).size() > 0) {
            methodsWithIfConditions.add(ctMethod);
            hasIfs = true;
        }
        return Map.entry("ifs", hasIfs);
    }

    private Map.Entry<String, Boolean> getSwitches(CtMethod<?> ctMethod) {
        boolean hasSwitches = false;
        if (ctMethod.getElements(new TypeFilter<>(CtSwitch.class)).size() > 0) {
            methodsWithSwitchStatements.add(ctMethod);
            hasSwitches = true;
        }
        return Map.entry("switches", hasSwitches);
    }

    private Map.Entry<String, Boolean> getConditionals(CtMethod<?> ctMethod) {
        boolean hasConditionals = false;
        if (ctMethod.getElements(new TypeFilter<>(CtConditional.class)).size() > 0) {
            methodsWithConditionalOperators.add(ctMethod);
            hasConditionals = true;
        }
        return Map.entry("conditionals", hasConditionals);
    }

    private Map.Entry<String, Boolean> getNumberOfStatements(CtMethod<?> ctMethod) {
        boolean hasMultipleStatements = false;
        if (ctMethod.getBody().getStatements().size() > 1) {
            methodsWithMultipleStatements.add(ctMethod);
            hasMultipleStatements = true;
        }
        return Map.entry("multiple_statements", hasMultipleStatements);
    }

    private Map.Entry<String, Boolean> getLocalVariables(CtMethod<?> ctMethod) {
        boolean hasLocalVariables = false;
        if (ctMethod.getElements(new TypeFilter<>(CtLocalVariable.class)).size() > 0) {
            methodsWithLocalVariables.add(ctMethod);
            hasLocalVariables = true;
        }
        return Map.entry("local_variables", hasLocalVariables);
    }

    private Map.Entry<String, Boolean> getReturns(CtMethod<?> ctMethod) {
        boolean returnsValue = false;
        if (ctMethod.getElements(new TypeFilter<>(CtReturn.class)).size() > 0) {
            methodsReturningAValue.add(ctMethod);
            returnsValue = true;
        } else {
            methodsNotReturningAValue.add(ctMethod);
        }
        return Map.entry("returns", returnsValue);
    }

    private Map.Entry<String, Boolean> getLoops(CtMethod<?> ctMethod) {
        boolean hasLoops = false;
        if (ctMethod.getElements(new TypeFilter<>(CtLoop.class)).size() > 0) {
            methodsWithLoops.add(ctMethod);
            hasLoops = true;
        }
        return Map.entry("loops", hasLoops);
    }

    private Map.Entry<String, Boolean> getParameters(CtMethod<?> ctMethod) {
        boolean hasParameters = false;
        if (ctMethod.getParameters().size() > 0) {
            methodsWithParameters.add(ctMethod);
            hasParameters = true;
        }
        return Map.entry("parameters", hasParameters);
    }

    private Map.Entry<String, Boolean> returnsPrimitives(CtMethod<?> ctMethod) {
        boolean returnsPrimitives = false;
        if (ctMethod.getType().isPrimitive() && !ctMethod.getType().getSimpleName().equals("void")) {
            methodsReturningAPrimitive.add(ctMethod);
            returnsPrimitives = true;
        }
        return Map.entry("returns_primitives", returnsPrimitives);
    }

    public Map<CtMethod<?>, Map<String, Boolean>> tagMethod(CtMethod<?> method) {
        Map<CtMethod<?>, Map<String, Boolean>> methodTags = new HashMap<>();

        Map<String, Boolean> tagMap = Map.ofEntries(
                isStatic(method),
                getIfs(method),
                getConditionals(method),
                getSwitches(method),
                getNumberOfStatements(method),
                getLocalVariables(method),
                getReturns(method),
                returnsPrimitives(method),
                getLoops(method),
                getParameters(method));
        methodTags.put(method, tagMap);
        return methodTags;
    }

    public void generateReport(CtMethod<?> candidateMethod) {
        allMethodTags.putAll(tagMethod(candidateMethod));
    }

    public Map<CtMethod<?>, Map<String, Boolean>> getAllMethodTags() {
        return allMethodTags;
    }

    @Override
    public String toString() {
        return "CandidateTagger{" +
                "methodsReturningAValue=" + methodsReturningAValue.size() +
                ", methodsReturningAPrimitive=" + methodsReturningAPrimitive.size() +
                ", staticMethods=" + staticMethods.size() +
                ", methodsWithParameters=" + methodsWithParameters.size() +
                ", methodsWithIfConditions=" + methodsWithIfConditions.size() +
                ", methodsWithConditionalOperators=" + methodsWithConditionalOperators.size() +
                ", methodsWithLoops=" + methodsWithLoops.size() +
                ", methodsWithLocalVariables=" + methodsWithLocalVariables.size() +
                ", methodsWithSwitchStatements=" + methodsWithSwitchStatements.size() +
                ", methodsWithMultipleStatements=" + methodsWithMultipleStatements.size() +
                '}';
    }

    @Override
    public boolean isToBeProcessed(CtMethod<?> candidate) {
        return candidate.getAllMetadata().containsKey("pure");
    }

    @Override
    public void process(CtMethod<?> method) {
        if (isToBeProcessed(method)) {
            generateReport(method);
        }
    }
}
