package se.kth.castor.pankti.generate.util;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.xml.XppReader;
import org.apache.commons.text.StringEscapeUtils;
import org.xmlpull.mxp1.MXParser;
import spoon.Launcher;
import spoon.compiler.SpoonResource;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class TestGeneratorUtil {
    public static Launcher launcher;
    public static String testFormat = "xml";

    public CtMethod<?> generateDeserializationMethod(Factory factory, String serializedObjectSource) {
        String methodName = String.format("deserializeObjectFrom%s", serializedObjectSource);
        CtTypeParameter typeParameter = factory.createTypeParameter().setSimpleName("T");
        CtTypeReference typeReference = factory.createCtTypeReference(Object.class).setSimpleName("T");
        CtMethod<?> deserializationMethod = factory.createMethod().setSimpleName(methodName);
        deserializationMethod.setModifiers(Collections.singleton(ModifierKind.PRIVATE));
        deserializationMethod.setFormalCtTypeParameters(Collections.singletonList(typeParameter));
        deserializationMethod.setType(typeReference);
        CtStatement returnStatement = factory.createCodeSnippetStatement("return (T) xStream.fromXML(serializedObjectString)");
        CtBlock<?> methodBody = factory.createBlock();
        methodBody.addStatement(returnStatement);
        deserializationMethod.setBody(methodBody);
        return deserializationMethod;
    }


    /**
     * Creates a resource file for long serialized XML profiles
     */
    public String createLongObjectStringFile(String methodIdentifier, String profileType, String longObjectStr) {
        String fileName = "";
        try {
            methodIdentifier = methodIdentifier.replaceAll("\\[]", "_arr_");
            File longObjectStrFile = new File("./output/object-data/" + methodIdentifier + "-" + profileType + "." + testFormat);
            longObjectStrFile.getParentFile().mkdirs();
            FileWriter myWriter = new FileWriter(longObjectStrFile);
            myWriter.write(longObjectStr);
            myWriter.close();
            SpoonResource newResource = SpoonResourceHelper.createResource(longObjectStrFile);
            launcher.addInputResource(longObjectStrFile.getAbsolutePath());
            fileName = newResource.getName();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileName;
    }

    public CtLocalVariable<String> addStringVariableToTestMethod(Factory factory, String fieldName, String fieldValue) {
        fieldValue = StringEscapeUtils.escapeJava(fieldValue).replaceAll("\\\\n", "\" +\n\"");
        CtExpression<String> variableExpression = factory.createCodeSnippetExpression(
                "\n\"" + fieldValue + "\""
        );
        return factory.createLocalVariable(
                factory.createCtTypeReference(String.class),
                fieldName,
                variableExpression
        );
    }

    public String getObjectProfileType(String type) {
        return type.substring(0, 1).toUpperCase() + type.substring(1);
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

    public CtLocalVariable<?> addFileVariableToDeserializationMethod(Factory factory) {
        CtExpression<String> fileVariableExpression = factory.createCodeSnippetExpression(
                "new File(classLoader.getResource(serializedObjectFilePath).getFile())"
        );
        CtLocalVariable<?> fileVariable = factory.createLocalVariable(
                factory.createCtTypeReference(File.class),
                "serializedObjectFile",
                fileVariableExpression);
        return fileVariable;
    }

    public CtStatement addFileVariableToTestMethod(Factory factory, String fileName, String type) {
        type = getObjectProfileType(type);
        String fileVariableName = "file" + type;
        String sanitizedFileName = fileName.replaceAll("\\[]", "_arr_");
        // Create file
        CtExpression<String> fileVariableExpression = factory.createCodeSnippetExpression(
                "new File(classLoader.getResource(\"" + sanitizedFileName + "\").getFile())"
        );
        CtLocalVariable<?> fileVariable = factory.createLocalVariable(
                factory.createCtTypeReference(File.class),
                fileVariableName,
                fileVariableExpression);
        return fileVariable;
    }

    public CtStatementList addAndReadFromScannerInDeserializationMethod(Factory factory) {
        CtStatementList scanningStatements = factory.createStatementList();
        // ClassLoader classLoader = getClass().getClassLoader();
        scanningStatements.addStatement(addClassLoaderVariableToTestMethod(factory));
        // File serializedObjectFile = new File(classLoader.getResource(serializedObjectFilePath).getFile());
        scanningStatements.addStatement(addFileVariableToDeserializationMethod(factory));
        String scannerVariableName = "scanner";
        String objectStringVariable = "serializedObjectString";
        // Create scanner
        CtExpression<String> scannerVariableExpression = factory.createCodeSnippetExpression(
                "new Scanner(serializedObjectFile)"
        );
        CtLocalVariable<?> scannerVariable = factory.createLocalVariable(
                factory.createCtTypeReference(Scanner.class),
                scannerVariableName,
                scannerVariableExpression
        );
        // Read object file from scanner
        CtExpression<String> variableExpression = factory.createCodeSnippetExpression(
                scannerVariableName + ".useDelimiter(\"\\\\A\").next()"
        );
        CtLocalVariable<?> stringVariable = factory.createLocalVariable(
                factory.createCtTypeReference(String.class),
                objectStringVariable,
                variableExpression
        );
        scanningStatements.addStatement(scannerVariable);
        scanningStatements.addStatement(stringVariable);
        return scanningStatements;
    }

    public List<CtStatement> addScannerVariableToTestMethod(Factory factory, String fileName, String type) {
        type = getObjectProfileType(type);
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
        String scannerVariableName = "scanner" + getObjectProfileType(type);
        String xmlVariableName = type + "ObjectStr";
        CtExpression<String> variableExpression = factory.createCodeSnippetExpression(
                scannerVariableName + ".useDelimiter(\"\\\\A\").next()"
        );
        return factory.createLocalVariable(
                factory.createCtTypeReference(String.class),
                xmlVariableName,
                variableExpression
        );
    }

    public CtStatement parseParamObjectsFromFileOrString(Factory factory, String fileOrString) {
        return factory.createCodeSnippetStatement(String.format(
                "Object[] paramObjects = deserializeObject(%s)",
                fileOrString));
    }

    public String findObjectBoxType(CtTypeReference typeReference) {
        if (typeReference.isPrimitive())
            return typeReference.box().getSimpleName();
        else return typeReference.getQualifiedName().replaceAll("\\$", ".");
    }

    // Gets method param list as _param1,param2,param3
    public static String getParamListPostFix(List<String> paramList) {
        if (paramList.size() == 0)
            return "";
        return (paramList.size() == 1 & paramList.get(0).isEmpty())
                ? "" : "_" + String.join(",", paramList);
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

    /**
     * This method transforms a xml object string generated by xStream to a json string.
     * The json string should be able to be deserialized to the object by xStream+JettisonMappedXmlDriver
     * (Because the transformation follows the same format that xStream does)
     *
     * @param objectStr the serialized object string in xml
     * @return the identical json string
     */
    public String transformXML2JSON(String objectStr) {
        HierarchicalStreamReader sourceReader = new XppReader(new StringReader(objectStr), new MXParser());

        StringWriter buffer = new StringWriter();
        JettisonMappedXmlDriver jettisonDriver = new JettisonMappedXmlDriver();
        HierarchicalStreamWriter destinationWriter = jettisonDriver.createWriter(buffer);

        HierarchicalStreamCopier copier = new HierarchicalStreamCopier();
        copier.copy(sourceReader, destinationWriter);

        return buffer.toString();
    }
}
