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
        List<String> attributesToRemove = List.of("uuid", "parent-uuid");
        for (String attributeToRemove : attributesToRemove) {
            if (thisNode.getAttributes().getNamedItem(attributeToRemove) != null)
                thisNode.getAttributes().removeNamedItem(attributeToRemove);
        }
    }

    public Map<String, String> parseXMLInFile(File inputFile) throws Exception {
        List<String> rawXMLObjects = new ArrayList<>();
        Map<String, String> uuidRawXMLObjectMap = new LinkedHashMap<>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        InputStream wellFormedXML = addRootElementToXMLFile(inputFile);

        Document doc = dBuilder.parse(wellFormedXML);

        DOMImplementationLS ls = (DOMImplementationLS) doc.getImplementation();
        LSSerializer ser = ls.createLSSerializer();

        Node rootNode = doc.getDocumentElement();
        rootNode.normalize();
        NodeList childNodes = rootNode.getChildNodes();

        List<String> attributes = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node thisNode = childNodes.item(i);
            if (thisNode.hasAttributes()) {
                for (int j = 0; j < thisNode.getAttributes().getLength(); j++) {
                    attributes.add(thisNode.getAttributes().item(j).getNodeValue());
                }
                removeAddedAttributes(thisNode);
            } else {
                // if no nested invocations, add a random attribute since we have to return a map
                // TODO: can be improved
                attributes.add(UUID.randomUUID().toString());
            }
            String rawXMLForObject = ser.writeToString(thisNode);
            rawXMLObjects.add(cleanUpRawObjectXML(rawXMLForObject));
        }
        rawXMLObjects.removeAll(Collections.singleton(""));
        // Map <uuid, rawXML>
        for (int i = 0; i < rawXMLObjects.size(); i++) {
            uuidRawXMLObjectMap.put(attributes.get(i), rawXMLObjects.get(i));
        }
        return uuidRawXMLObjectMap;
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
            Map<String, String> uuidReceivingObjectsMap = parseXMLInFile(receivingObjectFile);
            List<String> uuids = new ArrayList<>(uuidReceivingObjectsMap.keySet());
            List<String> receivingObjects = new ArrayList<>(uuidReceivingObjectsMap.values());
            List<String> returnedOrReceivingPostObjects;

            if (!instrumentedMethod.getReturnType().equals("void")) {
                // Get objects from xxx-returned.xml for non-void methods
                File returnedObjectFile = findXMLFileByObjectType(basePath, postfix + returnedObjectFilePostfix);
                returnedOrReceivingPostObjects = new ArrayList<>(parseXMLInFile(returnedObjectFile).values());
            } else {
                // Get objects from xxx-receiving-post.xml for void methods
                File receivingPostObjectFile = findXMLFileByObjectType(basePath, postfix + receivingPostObjectFilePostfix);
                returnedOrReceivingPostObjects = new ArrayList<>(parseXMLInFile(receivingPostObjectFile).values());
            }

            // Get objects from xxx-params.xml
            List<String> paramObjects = new ArrayList<>();
            if (hasParams) {
                File paramObjectsFile = findXMLFileByObjectType(basePath, postfix + paramObjectsFilePostfix);
                paramObjects = new ArrayList<>(parseXMLInFile(paramObjectsFile).values());
            }

            List<SerializedObject> nestedSerializedObjects = new ArrayList<>();

            // Get objects from nested-xxx-params.xml and nested-xxx-returned.xml
            if (instrumentedMethod.hasMockableInvocations()) {
                List<String> nestedInvocations = MockGeneratorUtil.getListOfInvocationsFromNestedMethodMap(instrumentedMethod.getNestedMethodMap());
                System.out.println("List of nested invocations to mock: " + nestedInvocations);
                List<String> nestedParamObjects = new ArrayList<>();
                List<String> nestedUuids = new ArrayList<>();
                List<String> nestedReturnedObjects = new ArrayList<>();
                List<String> nestedInvocationFQNs = new ArrayList<>();
                for (String invocation : nestedInvocations) {
                    String declaringType = MockGeneratorUtil.getDeclaringTypeToMock(invocation);
                    String mockedMethodWithParams = MockGeneratorUtil.getMockedMethodWithParams(invocation);
                    String methodName = MockGeneratorUtil.getMockedMethodName(mockedMethodWithParams);
                    List<String> params = MockGeneratorUtil.getParametersOfMockedMethod(mockedMethodWithParams);
                    String nestedInvocationPostfix = util.getParamListPostFix(params);
                    String filePathNestedParams = directory + nestedInvocationObjectFilePrefix + declaringType + "." + methodName +
                            nestedInvocationPostfix + paramObjectsFilePostfix;
                    Map<String, String> uuidNestedParamObjectsMap = parseXMLInFile(new File(filePathNestedParams));
                    for (Map.Entry<String, String> uuidObjectXML : uuidNestedParamObjectsMap.entrySet()) {
                        if (uuids.contains(uuidObjectXML.getKey())) {
                            nestedUuids.add(uuidObjectXML.getKey());
                            nestedParamObjects.add(uuidObjectXML.getValue());
                            nestedInvocationFQNs.add(declaringType + "." + mockedMethodWithParams);
                        }
                    }
                    String filePathNestedReturned = directory + nestedInvocationObjectFilePrefix + declaringType + "." + methodName +
                            nestedInvocationPostfix + returnedObjectFilePostfix;
                    Map<String, String> uuidNestedReturnedObjectsMap = parseXMLInFile(new File(filePathNestedReturned));
                    for (Map.Entry<String, String> objectXMLUUID : uuidNestedReturnedObjectsMap.entrySet()) {
                        if (uuids.contains(objectXMLUUID.getKey())) {
                            nestedReturnedObjects.add(objectXMLUUID.getValue());
                        }
                    }
                }

                for (int i = 0; i < nestedParamObjects.size(); i++) {
                    nestedSerializedObjects.add(new SerializedObject(
                            null,
                            nestedReturnedObjects.get(i),
                            null,
                            nestedParamObjects.get(i),
                            nestedUuids.get(i),
                            null,
                            nestedInvocationFQNs.get(i)
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
                                    .collect(Collectors.toList()),
                            null);
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
