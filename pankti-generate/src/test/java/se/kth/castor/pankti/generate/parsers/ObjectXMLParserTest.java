package se.kth.castor.pankti.generate.parsers;

import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.generate.data.ObjectProfileElement;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * These tests verify that (nested) objects profiles are correctly
 * parsed from XML files
 */
public class ObjectXMLParserTest {

    static ObjectXMLParser objectXMLParser = new ObjectXMLParser();

    @Test
    public void testObjectExtractionFromXMLFile() throws Exception {
        File objectXMLFile = new File("src/test/resources/param-objects.xml");
        List<ObjectProfileElement> xmlParams = objectXMLParser.parseXMLInFile(objectXMLFile);
        assertEquals(11, xmlParams.size());
    }

    @Test
    public void testNestedObjectProfileElementProperties() throws Exception {
        File objectXMLFile = new File("src/test/resources/nested-param-objects.xml");
        List<ObjectProfileElement> xmlNestedParams = objectXMLParser.parseXMLInFile(objectXMLFile);
        assertEquals(4, xmlNestedParams.size());
        assertEquals("99e603987ded4d09940854731c0c6bd0", xmlNestedParams.get(0).getUuid());
        assertEquals("2022-02-27T18:44:14.616Z", xmlNestedParams.get(0).getTimestamp().toString());
        assertEquals("<object-array><int>32</int></object-array>",
                xmlNestedParams.get(0).getRawXML().replaceAll("\\s", ""));
    }
}
