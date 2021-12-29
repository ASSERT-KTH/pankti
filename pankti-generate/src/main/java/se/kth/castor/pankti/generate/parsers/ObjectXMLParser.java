package se.kth.castor.pankti.generate.parsers;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import se.kth.castor.pankti.generate.generators.MockGeneratorUtil;
import se.kth.castor.pankti.generate.generators.TestGeneratorUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ObjectXMLParser {
    Set<SerializedObject> serializedObjects = new HashSet<>();
    private static final String nestedInvocationObjectFilePrefix = "nested-";
    private static final String receivingObjectFilePostfix = "-receiving.xml";
    private static final String paramObjectsFilePostfix = "-params.xml";
    private static final String returnedObjectFilePostfix = "-returned.xml";
    private static final String receivingPostObjectFilePostfix = "-receiving-post.xml";

    public InputStream addRootElementToXMLFile(File inputFile) throws FileNotFoundException {
        FileInputStream fis = new FileInputStream(inputFile);
        List<InputStream> streams =
                Arrays.asList(
                        new ByteArrayInputStream("<root>".getBytes()),
                        fis,
                        new ByteArrayInputStream("</root>".getBytes()));
        return new SequenceInputStream(Collections.enumeration(streams));
    }

    public File findXMLFileByObjectType(String basePath, String type) {
        return new File(basePath + type);
    }

    public String cleanUpRawObjectXML(String rawXMLForObject) {
        rawXMLForObject = rawXMLForObject.replaceAll("(\\<\\?xml version=\"1\\.0\" encoding=\"UTF-16\"\\?>)", "");
        rawXMLForObject = rawXMLForObject.trim();
        rawXMLForObject = rawXMLForObject.replaceAll("(&amp;#x)(\\w+;)", "&#x$2");
        return rawXMLForObject;
    }

    private void removeAddedAttributes(Node thisNode) {
        List<String> attributesToRemove = List.of("uuid", "parent-uuid", "parent-invocation");
        for (String attributeToRemove : attributesToRemove) {
            if (thisNode.getAttributes().getNamedItem(attributeToRemove)!= null)
                thisNode.getAttributes().removeNamedItem(attributeToRemove);
        }
    }

    public Map<String, String> parseXMLInFile(File inputFile) throws Exception {
        List<String> rawXMLObjects = new ArrayList<>();
        Map<String, String> rawXMLObjectAndAttributes = new LinkedHashMap<>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        InputStream wellFormedXML = addRootElementToXMLFile(inputFile);

        Document doc = dBuilder.parse(wellFormedXML);

        DOMImplementationLS ls = (DOMImplementationLS) doc.getImplementation();
        LSSerializer ser = ls.createLSSerializer();

        Node rootNode = doc.getDocumentElement();
        rootNode.normalize();
        NodeList childNodes = rootNode.getChildNodes();

        StringBuilder attributes = new StringBuilder();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node thisNode = childNodes.item(i);
            if (thisNode.hasAttributes()) {
                if (thisNode.getAttributes().getNamedItem("parent-invocation")!= null)
                    thisNode.getAttributes().removeNamedItem("parent-invocation");

                for (int j = 0; j < thisNode.getAttributes().getLength(); j++) {
                    attributes.append(thisNode.getAttributes().item(j).getNodeValue());
                }
                removeAddedAttributes(thisNode);
            }
            String rawXMLForObject = ser.writeToString(thisNode);
            rawXMLObjects.add(cleanUpRawObjectXML(rawXMLForObject));
        }
        rawXMLObjects.removeAll(Collections.singleton(""));
        for (int i = 0; i < rawXMLObjects.size(); i++) {
            rawXMLObjectAndAttributes.put(rawXMLObjects.get(i), attributes.toString());
        }
        return rawXMLObjectAndAttributes;
    }

    // Create object profiles from object xml files
    public Set<SerializedObject> parseXML(String directory, String methodPath, InstrumentedMethod instrumentedMethod) {
        final String basePath = directory + methodPath;
        String postfix = "";
        TestGeneratorUtil util = new TestGeneratorUtil();

        try {
            boolean hasParams = instrumentedMethod.hasParams();
            if (hasParams) {
                postfix = util.getParamListPostFix(instrumentedMethod.getParamList());
            }

            // Get objects from xxx-receiving.xml
            File receivingObjectFile = findXMLFileByObjectType(basePath, postfix + receivingObjectFilePostfix);
            Map<String, String> receivingObjectsAndAttributes = parseXMLInFile(receivingObjectFile);
            List<String> receivingObjects = new ArrayList<>(receivingObjectsAndAttributes.keySet());
            List<String> uuids = new ArrayList<>(receivingObjectsAndAttributes.values());
            List<String> returnedOrReceivingPostObjects;

            if (!instrumentedMethod.getReturnType().equals("void")) {
                // Get objects from xxx-returned.xml for non-void methods
                File returnedObjectFile = findXMLFileByObjectType(basePath, postfix + returnedObjectFilePostfix);
                returnedOrReceivingPostObjects = new ArrayList<>(parseXMLInFile(returnedObjectFile).keySet());
            } else {
                // Get objects from xxx-receiving-post.xml for void methods
                File receivingPostObjectFile = findXMLFileByObjectType(basePath, postfix + receivingPostObjectFilePostfix);
                returnedOrReceivingPostObjects = new ArrayList<>(parseXMLInFile(receivingPostObjectFile).keySet());
            }

            // Get objects from xxx-params.xml
            List<String> paramObjects = new ArrayList<>();
            if (hasParams) {
                File paramObjectsFile = findXMLFileByObjectType(basePath, postfix + paramObjectsFilePostfix);
                paramObjects = new ArrayList<>(parseXMLInFile(paramObjectsFile).keySet());
            }

            List<SerializedObject> nestedSerializedObjects = new ArrayList<>();

            // Get objects from nested-xxx-params.xml and nested-xxx-returned.xml
            if (instrumentedMethod.hasMockableInvocations()) {
                List<String> nestedInvocations = MockGeneratorUtil.getListOfInvocationsFromNestedMethodMap(instrumentedMethod.getNestedMethodMap());
                System.out.println("List of invocations to mock: " + nestedInvocations);
                List<String> nestedParamObjects = new ArrayList<>();
                List<String> nestedUuids = new ArrayList<>();
                List<String> nestedReturnedObjects = new ArrayList<>();
                for (String invocation : nestedInvocations) {
                    String declaringType = MockGeneratorUtil.getDeclaringTypeToMock(invocation);
                    String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(invocation);
                    String methodName = MockGeneratorUtil.getMockedMethodName(mockedMethodWithParams);
                    List<String> params = MockGeneratorUtil.getParametersOfMockedMethod(mockedMethodWithParams);
                    String nestedInvocationPostfix = util.getParamListPostFix(params);
                    String filePathNestedParams = directory + nestedInvocationObjectFilePrefix + declaringType + "." + methodName +
                            nestedInvocationPostfix + paramObjectsFilePostfix;
                    System.out.println("Looking for nested params in file: " + filePathNestedParams);
                    Map<String, String> nestedParamObjectsAndAttributes = parseXMLInFile(new File(filePathNestedParams));
                    nestedParamObjects.addAll(nestedParamObjectsAndAttributes.keySet());
                    nestedUuids.addAll(nestedParamObjectsAndAttributes.values());
                    String filePathNestedReturned = directory + nestedInvocationObjectFilePrefix + declaringType + "." + methodName +
                            nestedInvocationPostfix + returnedObjectFilePostfix;
                    System.out.println("Looking for nested returned in file: " + filePathNestedReturned);
                    nestedReturnedObjects.addAll(parseXMLInFile(new File(filePathNestedReturned)).keySet());
                }

                for (int i = 0; i < nestedParamObjects.size(); i++) {
                    nestedSerializedObjects.add(new SerializedObject(
                            null,
                            nestedReturnedObjects.get(i),
                            null,
                            nestedParamObjects.get(i),
                            nestedUuids.get(i),
                            null
                    ));
                }

                System.out.println("Nested serialized objects: " + nestedSerializedObjects.size());
            }

            int serializedObjectCount = 0;
            for (int i = 0; i < receivingObjects.size(); i++) {
                if (!receivingObjects.get(i).isEmpty() && !returnedOrReceivingPostObjects.get(i).isEmpty()) {
                    String params = hasParams ? paramObjects.get(i) : "";
                    // Create object profiles from all serialized objects
                    int finalI = i;
                    SerializedObject serializedObject = new SerializedObject(
                            receivingObjects.get(i),
                            (!instrumentedMethod.getReturnType().equals("void") ? returnedOrReceivingPostObjects.get(i) : ""),
                            (instrumentedMethod.getReturnType().equals("void") ? returnedOrReceivingPostObjects.get(i) : ""),
                            params,
                            uuids.get(i),
                            nestedSerializedObjects.stream()
                                    .filter(nestedSerializedObject -> nestedSerializedObject.getUUID().equals(uuids.get(finalI)))
                                    .collect(Collectors.toList()));
                    serializedObjects.add(serializedObject);
                    serializedObjectCount++;
                }
            }
            System.out.println("Number of pairs/triples of object values: " + serializedObjectCount);
        } catch (FileNotFoundException e) {
            System.out.println("NO OBJECT FILES FOUND FOR " + basePath + " PARAMS" + postfix + " - SKIPPING");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return serializedObjects;
    }
}
