package se.kth.castor.pankti.generate.parsers;

import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.generate.data.ObjectProfileElement;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectXMLParserTest {

    @Test
    public void testObjectExtractionFromXMLFile() throws Exception {
        ObjectXMLParser objectXMLParser = new ObjectXMLParser();
        File objectXMLFile = new File("src/test/resources/param-objects.xml");
        List<ObjectProfileElement> xmlParams = objectXMLParser.parseXMLInFile(objectXMLFile);
        assertEquals(11, xmlParams.size());
    }
}
