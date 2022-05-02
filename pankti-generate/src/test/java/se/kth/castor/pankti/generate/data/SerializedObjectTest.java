package se.kth.castor.pankti.generate.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.generate.parsers.CSVFileParser;
import se.kth.castor.pankti.generate.parsers.ObjectXMLParser;
import se.kth.castor.pankti.generate.util.TestGeneratorUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SerializedObjectTest {
    static List<InstrumentedMethod> instrumentedMethods;
    static ObjectXMLParser objectXMLParser = new ObjectXMLParser();
    static Set<SerializedObject> serializedObjects = new LinkedHashSet<>();
    static SerializedObject parent;
    static List<SerializedObject> nested = new ArrayList<>();

    @BeforeAll
    public static void readCSVFileAndFindParentAndNestedSerializedObjects() {
        instrumentedMethods = CSVFileParser.parseCSVFile("src/test/resources/invoked-methods-example.csv");
        serializedObjects = objectXMLParser.parseXML(
                "src/test/resources/", instrumentedMethods.get(3), true);
        parent = (SerializedObject) serializedObjects.toArray()[0];
        nested = parent.getNestedSerializedObjects();
    }

    @Test
    public void testThatOneParentSerializedObjectIsFound() {
        assertEquals(1, serializedObjects.size());
    }

    @Test
    public void testThatNestedSerializedObjectsAreFoundIfTheyExistAndRickIsTrue() {
        assertEquals(1, nested.size());
    }

    @Test
    public void testThatNestedSerializedObjectsAreOrderedByTimestamp() {
        assertEquals("2022-05-01T12:35:39.509Z", nested.get(0).getInvocationTimestamp().toString());
        for (int i = 1; i < nested.size(); i++) {
            assertTrue(nested.get(i - 1).getInvocationTimestamp().isBefore(nested.get(i).getInvocationTimestamp()));
        }
    }

    @Test
    public void testThatSerializedObjectFQNsAreFound() {
        assertEquals("java.io.OutputStream.write(byte[])",
                nested.get(0).getInvocationFQN());
    }

    @Test
    public void testThatParentReceivingObjectTypeIsIdentified() {
        assertEquals("org.apache.pdfbox.filter.DCTFilter", parent.getObjectType(parent.getReceivingObject()));
    }

    @Test
    public void testThatNestedTypeInfoIsIdentified() {
        assertNull(nested.get(0).getReceivingObject());
        assertNull(nested.get(0).getNestedSerializedObjects(),
                "nested serialized object should not have its own nested objects");
        assertEquals("<object-array><byte-array/></object-array>",
                nested.get(0).getParamObjects().replaceAll("\\s", ""));
        assertEquals("<null/>", nested.get(0).getReturnedObject(),
                "The returned object for this serialized object should be null," +
                        " since it corresponds to a method that returns void");
        assertTrue(new TestGeneratorUtil().returnedObjectIsNull(nested.get(0).getReturnedObject()));
    }
}
