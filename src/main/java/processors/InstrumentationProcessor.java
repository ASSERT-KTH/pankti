package processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public class InstrumentationProcessor extends AbstractProcessor<CtMethod<?>> {
    final static String VARIABLENAME = "returnedExpression";

    // Create a new variable of returned type, add to method beginning
    public void insertReturnedVariableInBeginning(CtMethod<?> method) {
        // <method return type> returnedExpression;
        CtCodeSnippetStatement returnDeclaration = getFactory().Core().createCodeSnippetStatement();
        final String value = String.format("%s %s", method.getType(), VARIABLENAME);
        returnDeclaration.setValue(value);
        method.getBody().insertBegin(returnDeclaration);
    }

    // Assign value to created variable and return it
    public void assignValueToVariable(CtMethod<?> method) {
        for (CtBlock<?> block : method.getElements(new TypeFilter<>(CtBlock.class))) {
            for (int i = 0; i < block.getStatements().size(); i++) {
                CtStatement thisStatement = block.getStatement(i);
                if (thisStatement instanceof CtReturn) {
                    CtReturn<?> ctReturn = thisStatement.getElements(new TypeFilter<>(CtReturn.class)).get(0);
                    // returnedExpression = <returned expression>;
                    CtCodeSnippetStatement returnDefinition = getFactory().Core().createCodeSnippetStatement();
                    final String value = String.format("%s = %s", VARIABLENAME, ctReturn.getReturnedExpression());
                    returnDefinition.setValue(value);
                    thisStatement.insertBefore(returnDefinition);
                    // return returnedExpression;
                    CtCodeSnippetStatement returnStatement = getFactory().Core().createCodeSnippetStatement();
                    final String returnStatementValue = String.format("return %s", VARIABLENAME);
                    returnStatement.setValue(returnStatementValue);
                    thisStatement.insertBefore(returnStatement);
                    ctReturn.delete();
                }
            }
        }
    }

    @Override
    public boolean isToBeProcessed(CtMethod<?> candidate) {
        return candidate.getAllMetadata().containsKey("pure");
    }

    // TODO: return from a switch case
    public void process(CtMethod<?> method) {
        if (isToBeProcessed(method)) {
            insertReturnedVariableInBeginning(method);
            assignValueToVariable(method);
        }
    }

    @Override
    public String toString() {
        return "InstrumentationProcessor{" +
                "Pure methods instrumented" +
                "}";
    }
}
