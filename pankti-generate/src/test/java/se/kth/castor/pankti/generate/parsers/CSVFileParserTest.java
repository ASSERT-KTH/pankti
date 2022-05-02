package se.kth.castor.pankti.generate.parsers;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.kth.castor.pankti.generate.data.InstrumentedMethod;
import se.kth.castor.pankti.generate.data.NestedInvocation;
import se.kth.castor.pankti.generate.data.SerializedObject;
import se.kth.castor.pankti.generate.util.MethodInvocationUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests verify that the CSV with invoked methods is correctly parsed
 * into a list of InstrumentedMethod objects, with their corresponding
 * NestedInvocation fields
 */
public class CSVFileParserTest {
    static List<InstrumentedMethod> instrumentedMethods;
    static ObjectXMLParser objectXMLParser = new ObjectXMLParser();

    @BeforeAll
    public static void readCSVFile() {
        instrumentedMethods = CSVFileParser.parseCSVFile("src/test/resources/invoked-methods-example.csv");
    }

    @Test
    public void testThatInstrumentedMethodsAreFound() {
        assertEquals(6, instrumentedMethods.size(),
                "There are 6 instrumented methods in the invoked methods CSV file");
    }

    @Test
    public void testInstrumentedMethodProperties() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(3);
        assertEquals("org.apache.pdfbox.filter.DCTFilter",
                instrumentedMethod.getParentFQN(),
                "The FQN of the parent should be correctly parsed");
        assertEquals("DCTFilter", instrumentedMethod.getParentSimpleName(),
                "The simple name of the parent should be extracted from its FQN");
        assertEquals("decode", instrumentedMethod.getMethodName(),
                "The name of the instrumented method is parsed");
        assertEquals("org.apache.pdfbox.filter.DCTFilter.decode",
                instrumentedMethod.getFullMethodPath());
        assertEquals("org.apache.pdfbox.filter.DecodeResult", instrumentedMethod.getReturnType(),
                "The return type of the method should be parsed");
        assertTrue(instrumentedMethod.hasMockableInvocations());
        assertTrue(instrumentedMethod.hasParams(), "This instrumented method has parameters");
        assertEquals(5, instrumentedMethod.getParamList().size(), "This method has no parameter");
        assertEquals("java.io.InputStream",
                instrumentedMethod.getParamList().get(0),
                "The parameter FQN of this method is correctly parsed");
        assertEquals("public", instrumentedMethod.getVisibility());
    }

    @Test
    public void testNestedInstrumentedMethodsProperties() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(0);
        assertEquals(2, instrumentedMethod.getNestedInvocations().size(),
                "Nested invocations for an instrumented method are found");
        assertEquals("org.apache.pdfbox.io.RandomAccessRead.length()",
                instrumentedMethod.getNestedInvocations().get(0).getInvocation(),
                "The FQN of the nested invocation is correctly set");
        assertEquals("DOMAIN", instrumentedMethod.getNestedInvocations().get(0).getInvocationMode(),
                "This nested invocation corresponds to a domain method");
        assertEquals("FIELD", instrumentedMethod.getNestedInvocations().get(0).getInvocationTargetType(),
                "This nested invocation is made on a parameter");
    }

    @Test
    public void testThatNestedInvocationOnLibraryMethodMadeOnParameterIsFound() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(2);
        assertEquals(3, instrumentedMethod.getNestedInvocations().size());
        assertEquals("org.apache.pdfbox.io.RandomAccessRead.isEOF()",
                instrumentedMethod.getNestedInvocations().get(0).getInvocation());
        assertEquals("DOMAIN", instrumentedMethod.getNestedInvocations().get(0).getInvocationMode());
        assertEquals("FIELD", instrumentedMethod.getNestedInvocations().get(0).getInvocationTargetType());
        assertEquals("private",
                instrumentedMethod.getNestedInvocations().get(0).getInvocationFieldsVisibilityMap().get("input"));
    }

    @Test
    public void testThatNestedInvocationOnFieldIsFound() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(3);
        assertEquals(1, instrumentedMethod.getNestedInvocations().size());
        NestedInvocation nestedInvocation = instrumentedMethod.getNestedInvocations().get(0);
        assertEquals("java.io.OutputStream.write(byte[])", nestedInvocation.getInvocation());
        assertEquals("LIBRARY", nestedInvocation.getInvocationMode());
        assertEquals("PARAMETER", nestedInvocation.getInvocationTargetType());
        assertNull(nestedInvocation.getInvocationFieldsVisibilityMap());
        assertEquals("void", nestedInvocation.getInvocationReturnType());
    }

    @Test
    public void testThatFieldNameAndVisibilityAreNullIfInvocationIsOnParameter() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(3);
        NestedInvocation nestedInvocation = instrumentedMethod.getNestedInvocations().get(0);
        assertNull(nestedInvocation.getInvocationFieldsVisibilityMap());
    }

    @Test
    public void testThatFieldNameAndVisibilityAreIdentified() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(0);
        assertEquals("available", instrumentedMethod.getMethodName());
        List<NestedInvocation> nestedInvocations = instrumentedMethod.getNestedInvocations();
        assertNull(nestedInvocations.get(0).getInvocationParamIndex());
        assertEquals(2, nestedInvocations.size());
        assertTrue(nestedInvocations.get(0).getInvocationFieldsVisibilityMap().containsKey("input"));
        assertEquals("private", nestedInvocations.get(0).getInvocationFieldsVisibilityMap().get("input"));
        for (Map.Entry<String, String> entry : nestedInvocations.get(0).getInvocationFieldsVisibilityMap().entrySet()) {
            assertTrue(nestedInvocations.get(0).isTargetFieldPrivate(entry));
            assertEquals("input", nestedInvocations.get(0).getTargetFieldName(entry));
        }
    }

    @Test
    public void testThatParentSerializedObjectsAreFoundWhenRickIsFalse() {
        Set<SerializedObject> serializedObjects =
                objectXMLParser.parseXML("src/test/resources/", instrumentedMethods.get(3), false);
        assertEquals(1, serializedObjects.size());
        SerializedObject first = (SerializedObject) serializedObjects.toArray()[0];
        assertEquals("org.apache.pdfbox.filter.DCTFilter",
                first.getObjectType(first.getReceivingObject()));
        assertEquals("DCTFilter",
                MethodInvocationUtil.getDeclaringTypeSimpleNameFromFQN(first.getObjectType(
                        first.getReceivingObject())));
        assertEquals("b1d403f897ee46a69ddc1b8f4e0bd7c2", first.getUUID());
//        assertEquals(1, first.getNestedSerializedObjects().size());
    }

    @Test
    public void testThatNestedSerializedObjectsAreNotFoundIfNoNestedInvocationsAndRickIsTrue() {
        Set<SerializedObject> serializedObjects =
                objectXMLParser.parseXML("src/test/resources/", instrumentedMethods.get(3), true);
        assertEquals(1, serializedObjects.size());
        SerializedObject first = (SerializedObject) serializedObjects.toArray()[0];
        assertEquals(1, first.getNestedSerializedObjects().size());
    }
}
