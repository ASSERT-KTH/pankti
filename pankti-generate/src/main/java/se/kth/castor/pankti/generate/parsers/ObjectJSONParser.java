package se.kth.castor.pankti.generate.parsers;

import se.kth.castor.pankti.generate.generators.TestGeneratorUtil;

import java.io.*;
import java.util.*;

public class ObjectJSONParser {
    Set<SerializedObject> serializedObjects = new HashSet<>();
    private static final String receivingObjectFilePostfix = "-receiving.json";
    private static final String paramObjectsFilePostfix = "-params.json";
    private static final String returnedObjectFilePostfix = "-returned.json";

    public File findJSONFileByObjectType(String basePath, String type) {
        return new File(basePath + type);
    }

    public List<String> parseJSONInFile(File inputFile) throws Exception {
        List<String> rawJSONObjects = new ArrayList<>();

        BufferedReader bufferedReader = new BufferedReader(new FileReader(inputFile));

        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            rawJSONObjects.add(line);
        }

        return rawJSONObjects;
    }

    public Set<SerializedObject> parseJSON(String basePath, InstrumentedMethod instrumentedMethod) {
        String postfix = "";
        try {
            boolean hasParams = instrumentedMethod.hasParams();
            if (hasParams) {
                TestGeneratorUtil util = new TestGeneratorUtil();
                postfix = util.getParamListPostFix(instrumentedMethod);
            }
            File receivingObjectFile = findJSONFileByObjectType(basePath, postfix + receivingObjectFilePostfix);
            List<String> receivingObjects = parseJSONInFile(receivingObjectFile);
            File returnedObjectFile = findJSONFileByObjectType(basePath, postfix + returnedObjectFilePostfix);
            List<String> returnedObjects = parseJSONInFile(returnedObjectFile);
            List<String> paramObjects = new ArrayList<>();
            if (hasParams) {
                File paramObjectsFile = findJSONFileByObjectType(basePath, postfix + paramObjectsFilePostfix);
                paramObjects = parseJSONInFile(paramObjectsFile);
            }

            for (int i = 0; i < receivingObjects.size(); i++) {
                if (!receivingObjects.get(i).isEmpty() && !returnedObjects.get(i).isEmpty()) {
                    String params = hasParams ? paramObjects.get(i) : "";
                    SerializedObject serializedObject = new SerializedObject(
                            receivingObjects.get(i),
                            returnedObjects.get(i),
                            params);
                    serializedObjects.add(serializedObject);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("NO OBJECT FILES FOUND FOR " + basePath + " PARAMS" + postfix + " - SKIPPING");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return serializedObjects;
    }
}
