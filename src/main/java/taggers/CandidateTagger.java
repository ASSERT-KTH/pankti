package taggers;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class CandidateTagger {

    private Map.Entry<String, Boolean> getIfs(CtMethod<?> ctMethod) {
        boolean hasIfs = false;
        if (ctMethod.getBody().getElements(new TypeFilter<>(CtIf.class)).size() > 0) {
            hasIfs = true;
        }
        return Map.entry("ifs", hasIfs);
    }

    private Map.Entry<String, Boolean> getSwitches(CtMethod<?> ctMethod) {
        boolean hasSwitches = false;
        if (ctMethod.getBody().getElements(new TypeFilter<>(CtSwitch.class)).size() > 0) {
            hasSwitches = true;
        }
        return Map.entry("switches", hasSwitches);
    }

    private Map.Entry<String, Boolean> getConditionals(CtMethod<?> ctMethod) {
        boolean hasConditionals = false;
        if (ctMethod.getBody().getElements(new TypeFilter<>(CtConditional.class)).size() > 0) {
            hasConditionals = true;
        }
        return Map.entry("conditionals", hasConditionals);
    }

    private Map.Entry<String, Boolean> getNumberOfStatements(CtMethod<?> ctMethod) {
        boolean hasMultipleStatements = false;
        if (ctMethod.getBody().getStatements().size() > 1) {
            hasMultipleStatements = true;
        }
        return Map.entry("multiple_statements", hasMultipleStatements);
    }

    private Map.Entry<String, Boolean> getLocalVariables(CtMethod<?> ctMethod) {
        boolean hasLocalVariables = false;
        if (ctMethod.getBody().getElements(new TypeFilter<>(CtLocalVariable.class)).size() > 0) {
            hasLocalVariables = true;
        }
        return Map.entry("local_variables", hasLocalVariables);
    }

    private Map.Entry<String, Boolean> getReturns(CtMethod<?> ctMethod) {
        boolean returnsValue = false;
        if (ctMethod.getBody().getElements(new TypeFilter<>(CtReturn.class)).size() > 0)
            returnsValue = true;
        return Map.entry("returns", returnsValue);
    }

    private Map.Entry<String, Boolean> getLoops(CtMethod<?> ctMethod) {
        boolean hasLoops = false;
        if (ctMethod.getBody().getElements(new TypeFilter<>(CtLoop.class)).size() > 0)
            hasLoops = true;
        return Map.entry("loops", hasLoops);
    }

    private Map.Entry<String, Boolean> getParameters(CtMethod<?> ctMethod) {
        boolean hasParameters = false;
        if (ctMethod.getParameters().size() > 0)
            hasParameters = true;
        return Map.entry("parameters", hasParameters);
    }

    private Map.Entry<String, Boolean> returnsPrimitives(CtMethod<?> ctMethod) {
        boolean returnsPrimitives = false;
        if (ctMethod.getType().isPrimitive() && !ctMethod.getType().getSimpleName().equals("void"))
            returnsPrimitives = true;
        return Map.entry("returns_primitives", returnsPrimitives);
    }

    public Map<CtMethod<?>, Map<String, Boolean>> tagMethod(CtMethod<?> method) {
        Map<CtMethod<?>, Map<String, Boolean>> methodTags = new HashMap<>();

        Map<String, Boolean> tagMap = Map.ofEntries(getIfs(method),
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
}
