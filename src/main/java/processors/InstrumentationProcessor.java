package processors;

import annotations.Pure;
import spoon.processing.AbstractAnnotationProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public class InstrumentationProcessor extends AbstractAnnotationProcessor<Pure, CtMethod<?>> {
    final static String VARIABLENAME = "returnedExpression";

    // Create a new variable of returned type, add to method beginning
    public void insertReturnedVariableInBeginning(CtMethod<?> method) {
        CtCodeSnippetStatement returnDeclaration = getFactory().Core().createCodeSnippetStatement();
        final String value = String.format("%s = %s", method.getType(), VARIABLENAME);
        returnDeclaration.setValue(value);
        method.getBody().insertBegin(returnDeclaration);
    }

    // Assign value to created variable
    public void assignValueToVariable(CtMethod<?> method) {
        for (CtBlock<?> block : method.getElements(new TypeFilter<>(CtBlock.class))) {
            for (int i = 0; i < block.getStatements().size(); i++) {
                CtStatement thisStatement = block.getStatement(i);
                if (thisStatement instanceof CtReturn) {
                    CtReturn<?> ctReturn = thisStatement.getElements(new TypeFilter<>(CtReturn.class)).get(0);
                    CtCodeSnippetStatement returnDefinition = getFactory().Core().createCodeSnippetStatement();
                    final String value = String.format("%s = %s", VARIABLENAME, ctReturn.getReturnedExpression());
                    returnDefinition.setValue(value);
                    thisStatement.insertBefore(returnDefinition);
                    ctReturn.delete();
                }
            }
        }
    }

    // Return created variable at the end of method
    public void returnVariable(CtMethod<?> method) {
        CtCodeSnippetStatement returnStatement = getFactory().Core().createCodeSnippetStatement();
        final String value = String.format("return %s", VARIABLENAME);
        returnStatement.setValue(value);
        method.getBody().insertEnd(returnStatement);
    }

    public void process(Pure pureAnnotation, CtMethod<?> method) {
        insertReturnedVariableInBeginning(method);
        assignValueToVariable(method);
        returnVariable(method);
    }

    @Override
    public String toString() {
        return "InstrumentationProcessor{" +
                "Pure methods instrumented" +
                "}";
    }
}
