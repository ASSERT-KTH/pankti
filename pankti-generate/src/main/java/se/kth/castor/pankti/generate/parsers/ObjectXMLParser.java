package se.kth.castor.pankti.generate.parsers;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import se.kth.castor.pankti.generate.data.InstrumentedMethod;
import se.kth.castor.pankti.generate.data.NestedInvocation;
import se.kth.castor.pankti.generate.data.ObjectProfileElement;
import se.kth.castor.pankti.generate.data.SerializedObject;
import se.kth.castor.pankti.generate.util.MethodInvocationUtil;
import se.kth.castor.pankti.generate.util.TestGeneratorUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.time.Instant;
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
        List<String> attributesToRemove = List.of("uuid", "parent-uuid", "timestamp");
        for (String attributeToRemove : attributesToRemove) {
            if (thisNode.getAttributes().getNamedItem(attributeToRemove) != null)
                thisNode.getAttributes().removeNamedItem(attributeToRemove);
        }
    }

    public List<ObjectProfileElement> parseXMLInFile(File inputFile) throws Exception {
        List<ObjectProfileElement> objectProfileElements = new ArrayList<>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        InputStream wellFormedXML = addRootElementToXMLFile(inputFile);

        Document doc = dBuilder.parse(wellFormedXML);

        DOMImplementationLS ls = (DOMImplementationLS) doc.getImplementation();
        LSSerializer ser = ls.createLSSerializer();

        Node rootNode = doc.getDocumentElement();
        rootNode.normalize();
        NodeList childNodes = rootNode.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node thisNode = childNodes.item(i);
            ObjectProfileElement element = new ObjectProfileElement();

            // ignore newline xml elements => !thisNode.toString().equals("[#text: \n]")
            if (!thisNode.hasAttributes() & !thisNode.toString().equals("[#text: \n]")) {
                element.setUuid(null);
            }
            if (thisNode.hasAttributes() & !thisNode.toString().equals("[#text: \n]")) {
                for (int j = 0; j < thisNode.getAttributes().getLength(); j++) {
                    if (thisNode.getAttributes().item(j).getNodeName().contains("uuid")) {
                        element.setUuid(thisNode.getAttributes().item(j).getNodeValue()
                                .replace("-", ""));
                    }
                    if (thisNode.getAttributes().item(j).getNodeName().equals("timestamp")) {
                        element.setTimestamp(Instant.ofEpochMilli(
                                Long.parseLong(thisNode.getAttributes().item(j).getNodeValue())));
                    }
                }
                removeAddedAttributes(thisNode);
            }
            String rawXMLForObject = ser.writeToString(thisNode);
            String cleanedUpXML = cleanUpRawObjectXML(rawXMLForObject);
            if (!cleanedUpXML.equals("")) {
                element.setRawXML(cleanedUpXML);
                objectProfileElements.add(element);
            }

        }
        return objectProfileElements;
    }

    // Create object profiles from object xml files
    public Set<SerializedObject> parseXML(String directory,
                                          InstrumentedMethod instrumentedMethod,
                                          boolean generateMocks) {
        final String basePath = directory + instrumentedMethod.getFullMethodPath();
        String postfix = "";
        TestGeneratorUtil util = new TestGeneratorUtil();

        try {
            boolean hasParams = instrumentedMethod.hasParams();
            if (hasParams) {
                postfix = TestGeneratorUtil.getParamListPostFix(instrumentedMethod.getParamList());
            }

            // Get objects from xxx-receiving.xml
            File receivingObjectFile = findXMLFileByObjectType(basePath, postfix + receivingObjectFilePostfix);
            List<ObjectProfileElement> receivingElements = parseXMLInFile(receivingObjectFile);
            List<String> receivingObjects = new ArrayList<>();
            List<String> parentUUIDs = new ArrayList<>();
            for (ObjectProfileElement element : receivingElements) {
                receivingObjects.add(element.getRawXML());
                parentUUIDs.add(element.getUuid());
            }
            List<String> returnedOrReceivingPostObjects = new ArrayList<>();
            List<ObjectProfileElement> returnedOrReceivingPostElements;
            if (!instrumentedMethod.getReturnType().equals("void")) {
                // Get objects from xxx-returned.xml for non-void methods
                File returnedObjectFile = findXMLFileByObjectType(basePath, postfix + returnedObjectFilePostfix);
                returnedOrReceivingPostElements = parseXMLInFile(returnedObjectFile);
            } else {
                // Get objects from xxx-receiving-post.xml for void methods
                File receivingPostObjectFile = findXMLFileByObjectType(basePath, postfix + receivingPostObjectFilePostfix);
                returnedOrReceivingPostElements = parseXMLInFile(receivingPostObjectFile);
            }
            for (ObjectProfileElement returnedOrReceivingPostElement : returnedOrReceivingPostElements) {
                returnedOrReceivingPostObjects.add(returnedOrReceivingPostElement.getRawXML());
            }

            // Get objects from xxx-params.xml
            List<String> paramObjects = new ArrayList<>();
            if (hasParams) {
                File paramObjectsFile = findXMLFileByObjectType(basePath, postfix + paramObjectsFilePostfix);
                List<ObjectProfileElement> paramElements = parseXMLInFile(paramObjectsFile);
                for (ObjectProfileElement paramElement : paramElements) {
                    paramObjects.add(paramElement.getRawXML());
                }
            }

            List<SerializedObject> nestedSerializedObjects = new ArrayList<>();

            // Get objects from nested-xxx-params.xml and nested-xxx-returned.xml
            // Get nested xml if --rick
            if (generateMocks & instrumentedMethod.hasMockableInvocations()) {
                List<NestedInvocation> nestedInvocations = instrumentedMethod.getNestedInvocations();
                System.out.printf("%d nested invocations to mock: %s%n",
                        nestedInvocations.size(),
                        nestedInvocations);
                for (NestedInvocation nestedInvocation : nestedInvocations) {
                    String declaringType = MethodInvocationUtil.getDeclaringTypeFromInvocationFQN(nestedInvocation.getInvocation());
                    String mockedMethodWithParams = MethodInvocationUtil.getMethodWithParamsFromInvocationFQN(nestedInvocation.getInvocation());
                    String methodName = MethodInvocationUtil.getMethodName(mockedMethodWithParams);
                    List<String> params = MethodInvocationUtil.getMethodParams(mockedMethodWithParams);
                    String nestedInvocationPostfix = TestGeneratorUtil.getParamListPostFix(params);
                    List<String> nestedParamObjects = new ArrayList<>();
                    List<String> nestedUuids = new ArrayList<>();
                    List<String> nestedReturnedObjects = new ArrayList<>();
                    List<Instant> nestedTimestamps = new ArrayList<>();
                    List<String> nestedInvocationFQNs = new ArrayList<>();
                    String filePathNestedParams = directory + nestedInvocationObjectFilePrefix + declaringType + "." + methodName +
                            nestedInvocationPostfix + paramObjectsFilePostfix;
                    String filePathNestedReturned = directory + nestedInvocationObjectFilePrefix + declaringType + "." + methodName +
                            nestedInvocationPostfix + returnedObjectFilePostfix;
                    try {
                        List<ObjectProfileElement> nestedParamElements = parseXMLInFile(new File(filePathNestedParams));
                        for (ObjectProfileElement nestedParamElement : nestedParamElements) {
                            if (parentUUIDs.contains(nestedParamElement.getUuid())) {
                                nestedParamObjects.add(nestedParamElement.getRawXML());
                                nestedTimestamps.add(nestedParamElement.getTimestamp());
                                nestedInvocationFQNs.add(declaringType + "." + mockedMethodWithParams);
                            }
                        }
                        List<ObjectProfileElement> nestedReturnedElements = parseXMLInFile(new File(filePathNestedReturned));
                        for (ObjectProfileElement nestedReturnedElement : nestedReturnedElements) {
                            if (parentUUIDs.contains(nestedReturnedElement.getUuid())) {
                                nestedUuids.add(nestedReturnedElement.getUuid());
                                nestedReturnedObjects.add(nestedReturnedElement.getRawXML());
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
                                    nestedTimestamps.get(i),
                                    nestedInvocationFQNs.get(i)
                            ));
                        }
                    } catch (Exception e) {
                        System.out.println("NO NESTED OBJECT FILE - " + filePathNestedParams + " AND / OR " +
                                filePathNestedReturned + " - SKIPPING");
                        continue;
                    }
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
                            parentUUIDs.get(i),
                            nestedSerializedObjects.stream()
                                    .filter(Objects::nonNull)
                                    .filter(nestedSerializedObject -> nestedSerializedObject.getUUID().equals(parentUUIDs.get(finalI)))
                                    .collect(Collectors.toList()),
                            null,
                            null);
                    serializedObject.getNestedSerializedObjects()
                            .sort(Comparator.comparing(SerializedObject::getInvocationTimestamp));
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
