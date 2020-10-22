package se.kth.castor.pankti.generate.generators;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DummyClass {
    private final String finalStringField = "string1";
    private String stringField;
    private int[] intArrayField = {1, 2, 3};
    public int intField;
    public List<String> stringList = new ArrayList<String>();
    public Map<String, String> stringMap = new HashMap<String, String>();

    public DummyClass() {
        this.stringField = "string2";
        this.intField = 1;
        this.stringList.add("string1");
        this.stringList.add("string2");
        this.stringMap.put("key1", "value1");
        this.stringMap.put("key2", "value2");
    }
}

public class TestGeneratorUtilTest {
    @Test
    public void testTransformXML2JsonDirectly() {
        DummyClass dummyObject = new DummyClass();

        XStream xStream = new XStream();

        /*
         * Here we need to turn off XStream's hints for collections and arrays.
         * Because this step seems to be done by XStream during the serialization.
         * Only using TestGeneratorUtil.transformXML2JSON seems not be able to do that.
         * I will double-check if we could transform XML to JSON directly in a fully equal manner.
         * But I think TestGeneratorUtil.transformXML2JSON works fine, based on the next test case.
         */
        XStream xStreamJson = new XStream(new JettisonMappedXmlDriver(null, false));

        String expectedJsonString = xStreamJson.toXML(dummyObject);

        TestGeneratorUtil util = new TestGeneratorUtil();
        String jsonString = util.transformXML2JSON(xStream.toXML(dummyObject));

        assertEquals(expectedJsonString, jsonString);
    }

    /**
     * We use xStream to serialize a dummyObject to xml string, then the string is transformed directly to json.
     * We use xStream to deserialize the transformed json back to dummyObject.
     * If the transformed json string works, the dummyObject which is deserialized from json should be identical
     * to the dummyObject which is created at the beginning.
     */
    @Test
    public void testDeserializeTheTransformedJsonString() {
        DummyClass dummyObject = new DummyClass();

        XStream xStream = new XStream();
        XStream xStreamJson = new XStream(new JettisonMappedXmlDriver());

        TestGeneratorUtil util = new TestGeneratorUtil();
        String jsonString = util.transformXML2JSON(xStream.toXML(dummyObject));
        DummyClass deserializedObject = (DummyClass) xStreamJson.fromXML(jsonString);

        assertEquals(xStream.toXML(dummyObject), xStream.toXML(deserializedObject));
    }
}
