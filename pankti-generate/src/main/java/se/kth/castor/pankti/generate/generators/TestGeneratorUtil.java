package se.kth.castor.pankti.generate.generators;

import se.kth.castor.pankti.generate.parsers.InstrumentedMethod;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TestGeneratorUtil {
    public CtLocalVariable<String> addStringVariableToTestMethod(Factory factory, String fieldName, String fieldValue) {
        fieldValue = fieldValue.replaceAll("\\n", "\" +\n\"");
        CtExpression<String> variableExpression = factory.createCodeSnippetExpression(
                "\n\"" + fieldValue + "\""
        );
        return factory.createLocalVariable(
                factory.createCtTypeReference(String.class),
                fieldName,
                variableExpression
        );
    }

    public CtLocalVariable<?> addClassLoaderVariableToTestMethod(Factory factory) {
        CtExpression<String> variableExpression = factory.createCodeSnippetExpression(
                "getClass().getClassLoader()"
        );
        return factory.createLocalVariable(
                factory.createCtTypeReference(ClassLoader.class),
                "classLoader",
                variableExpression
        );
    }

    public List<CtStatement> addScannerVariableToTestMethod(Factory factory, String fileName, String type) {
        type = type.substring(0, 1).toUpperCase() + type.substring(1);
        String fileVariableName = "file" + type;
        String scannerVariableName = "scanner" + type;
        List<CtStatement> fileAndScannerStatements = new ArrayList<>();
        // Create file
        CtExpression<String> fileVariableExpression = factory.createCodeSnippetExpression(
                "new File(classLoader.getResource(\"" + fileName + "\").getFile())"
        );
        CtLocalVariable<?> fileVariable = factory.createLocalVariable(
                factory.createCtTypeReference(File.class),
                fileVariableName,
                fileVariableExpression);
        // Create scanner
        CtExpression<String> scannerVariableExpression = factory.createCodeSnippetExpression(
                "new Scanner(" + fileVariableName + ")"
        );
        CtLocalVariable<?> scannerVariable = factory.createLocalVariable(
                factory.createCtTypeReference(Scanner.class),
                scannerVariableName,
                scannerVariableExpression
        );
        fileAndScannerStatements.add(fileVariable);
        fileAndScannerStatements.add(scannerVariable);
        return fileAndScannerStatements;
    }

    public CtLocalVariable<String> readStringFromScanner(Factory factory, String type) {
        String scannerVariableName = "scanner" + type.substring(0, 1).toUpperCase() + type.substring(1);;
        String xmlVariableName = type + "XML";
        CtExpression<String> variableExpression = factory.createCodeSnippetExpression(
                scannerVariableName + ".useDelimiter(\"\\\\A\").next()"
        );
        return factory.createLocalVariable(
                factory.createCtTypeReference(String.class),
                xmlVariableName,
                variableExpression
        );
    }

    public String findObjectBoxType(CtTypeReference typeReference) {
        if (typeReference.isPrimitive())
            return typeReference.box().getSimpleName();
        else return typeReference.getQualifiedName().replaceAll("\\$", ".");
    }

    // Gets method param list as _param1,param2,param3
    public String getParamListPostFix(InstrumentedMethod instrumentedMethod) {
            return  "_" + String.join(",", instrumentedMethod.getParamList());
    }

    public boolean allMethodParametersArePrimitive(CtMethod<?> method) {
        for (CtParameter<?> parameter : method.getParameters()) {
            if (!parameter.getType().isPrimitive()) {
                return false;
            }
        }
        return true;
    }

    public boolean returnedObjectIsNull(String returnedXML) {
        return returnedXML.equals("<null/>");
    }
}
