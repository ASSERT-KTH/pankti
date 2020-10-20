package se.kth.castor.pankti.generate.parsers;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import se.kth.castor.pankti.generate.generators.TestGeneratorUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;

public class ObjectXMLParser {
    Set<SerializedObject> serializedObjects = new HashSet<>();
    private static final String receivingObjectFilePostfix = "-receiving.xml";
    private static final String paramObjectsFilePostfix = "-params.xml";
    private static final String returnedObjectFilePostfix = "-returned.xml";

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
        rawXMLForObject = rawXMLForObject.replaceAll("\\\\", "\\\\\\\\");
        rawXMLForObject = rawXMLForObject.replaceAll("\"", "\\\\\"");
        rawXMLForObject = rawXMLForObject.trim();
        rawXMLForObject = rawXMLForObject.replaceAll("(&amp;#x)(\\w+;)", "&#x$2");
        return rawXMLForObject;
    }

    public List<String> parseXMLInFile(File inputFile) throws Exception {
        List<String> rawXMLObjects = new ArrayList<>();
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
            String rawXMLForObject = ser.writeToString(thisNode);
            rawXMLObjects.add(cleanUpRawObjectXML(rawXMLForObject));
        }
        return rawXMLObjects;
    }

    public Set<SerializedObject> parseXML(String basePath, InstrumentedMethod instrumentedMethod) {
        String postfix = "";
        try {
            boolean hasParams = instrumentedMethod.hasParams();
            if (hasParams) {
                TestGeneratorUtil util = new TestGeneratorUtil();
                postfix = util.getParamListPostFix(instrumentedMethod);
            }
            File receivingObjectFile = findXMLFileByObjectType(basePath, postfix + receivingObjectFilePostfix);
            List<String> receivingObjects = parseXMLInFile(receivingObjectFile);
            File returnedObjectFile = findXMLFileByObjectType(basePath, postfix + returnedObjectFilePostfix);
            List<String> returnedObjects = parseXMLInFile(returnedObjectFile);
            List<String> paramObjects = new ArrayList<>();
            if (hasParams) {
                File paramObjectsFile = findXMLFileByObjectType(basePath, postfix + paramObjectsFilePostfix);
                paramObjects = parseXMLInFile(paramObjectsFile);
            }

            int serializedObjectCount = 0;
            for (int i = 0; i < receivingObjects.size(); i++) {
                if (!receivingObjects.get(i).isEmpty() && !returnedObjects.get(i).isEmpty()) {
                    String params = hasParams ? paramObjects.get(i) : "";
                    SerializedObject serializedObject = new SerializedObject(
                            receivingObjects.get(i),
                            returnedObjects.get(i),
                            params);
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
