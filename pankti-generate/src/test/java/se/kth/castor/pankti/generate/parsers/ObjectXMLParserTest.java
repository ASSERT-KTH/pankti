package se.kth.castor.pankti.generate.parsers;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectXMLParserTest {

    @Test
    public void testObjectExtractionFromXMLFile() throws Exception {
        ObjectXMLParser objectXMLParser = new ObjectXMLParser();
        File objectXMLFile = new File("src/test/resources/param-objects.xml");
        List<String> numberOfObjects = new ArrayList<>(objectXMLParser.parseXMLInFile(objectXMLFile).keySet());
        assertEquals(11, numberOfObjects.size());
    }
}
