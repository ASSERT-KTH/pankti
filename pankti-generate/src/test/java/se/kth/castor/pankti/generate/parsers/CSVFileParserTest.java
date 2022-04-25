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
        assertEquals(5, instrumentedMethods.size(),
                "There are 5 instrumented methods in the invoked methods CSV file");
    }

    @Test
    public void testInstrumentedMethodProperties() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(0);
        assertEquals("org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState",
                instrumentedMethod.getParentFQN(),
                "The FQN of the parent should be correctly parsed");
        assertEquals("PDExtendedGraphicsState", instrumentedMethod.getParentSimpleName(),
                "The simple name of the parent should be extracted from its FQN");
        assertEquals("copyIntoGraphicsState", instrumentedMethod.getMethodName(),
                "The name of the instrumented method is parsed");
        assertEquals("org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState.copyIntoGraphicsState",
                instrumentedMethod.getFullMethodPath());
        assertEquals("void", instrumentedMethod.getReturnType(),
                "The return type of the method should be parsed");
        assertTrue(instrumentedMethod.hasMockableInvocations());
        assertTrue(instrumentedMethod.hasParams(), "This instrumented method has parameter(s)");
        assertEquals(1, instrumentedMethod.getParamList().size(), "This method has one parameter");
        assertEquals("org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState",
                instrumentedMethod.getParamList().get(0),
                "The parameter FQN of this method is correctly parsed");
        assertEquals("public", instrumentedMethod.getVisibility());
    }

    @Test
    public void testNestedInstrumentedMethodsProperties() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(0);
        assertEquals(13, instrumentedMethod.getNestedInvocations().size(),
                "Nested invocations for an instrumented method are found");
        assertEquals("org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState.setLineWidth(float)",
                instrumentedMethod.getNestedInvocations().get(0).getInvocation(),
                "The FQN of the nested invocation is correctly set");
        assertEquals("DOMAIN", instrumentedMethod.getNestedInvocations().get(0).getInvocationMode(),
                "This nested invocation corresponds to a domain method");
        assertEquals("PARAMETER", instrumentedMethod.getNestedInvocations().get(0).getInvocationTargetType(),
                "This nested invocation is made on a parameter");
    }

    @Test
    public void testThatNestedInvocationOnLibraryMethodMadeOnParameterIsFound() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(2);
        assertEquals(1, instrumentedMethod.getNestedInvocations().size());
        assertEquals("java.util.Map.size()",
                instrumentedMethod.getNestedInvocations().get(0).getInvocation());
        assertEquals("LIBRARY", instrumentedMethod.getNestedInvocations().get(0).getInvocationMode());
        assertEquals("PARAMETER", instrumentedMethod.getNestedInvocations().get(0).getInvocationTargetType());
        assertNull(instrumentedMethod.getNestedInvocations().get(0).getInvocationFieldsVisibilityMap());
    }

    @Test
    public void testThatNestedInvocationOnFieldIsFound() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(3);
        assertEquals(2, instrumentedMethod.getNestedInvocations().size());
        NestedInvocation nestedInvocation = instrumentedMethod.getNestedInvocations().get(0);
        assertEquals("org.apache.pdfbox.cos.COSObjectKey.getNumber()", nestedInvocation.getInvocation());
        assertEquals("DOMAIN", nestedInvocation.getInvocationMode());
        assertEquals("FIELD", nestedInvocation.getInvocationTargetType());
        assertEquals(1, nestedInvocation.getInvocationFieldsVisibilityMap().size());
        assertEquals("long", nestedInvocation.getInvocationReturnType());
    }

    @Test
    public void testThatFieldNameAndVisibilityAreIdentified() {
        InstrumentedMethod instrumentedMethod = instrumentedMethods.get(3);
        NestedInvocation nestedInvocation = instrumentedMethod.getNestedInvocations().get(0);
        assertTrue(nestedInvocation.getInvocationFieldsVisibilityMap().containsKey("currentObjectKey"));
        assertEquals("private", nestedInvocation.getInvocationFieldsVisibilityMap().get("currentObjectKey"));
        for (Map.Entry<String, String> entry : nestedInvocation.getInvocationFieldsVisibilityMap().entrySet()) {
            assertTrue(nestedInvocation.isTargetFieldPrivate(entry));
            assertEquals("currentObjectKey", nestedInvocation.getTargetFieldName(entry));
        }
    }

    @Test
    public void testThatParentSerializedObjectsAreFoundWhenRickIsFalse() {
        Set<SerializedObject> serializedObjects =
                objectXMLParser.parseXML("src/test/resources/", instrumentedMethods.get(1), false);
        assertEquals(1, serializedObjects.size());
        SerializedObject first = (SerializedObject) serializedObjects.toArray()[0];
        assertEquals("org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters",
                first.getObjectType(first.getReceivingObject()));
        assertEquals("SetGraphicsStateParameters",
                MethodInvocationUtil.getDeclaringTypeSimpleNameFromFQN(first.getObjectType(
                        first.getReceivingObject())));
        assertEquals("032df387129349008a3bd3cb68e6203b", first.getUUID());
        assertEquals(0, first.getNestedSerializedObjects().size());
    }

    @Test
    public void testThatNestedSerializedObjectsAreNotFoundIfNoNestedInvocationsAndRickIsTrue() {
        Set<SerializedObject> serializedObjects =
                objectXMLParser.parseXML("src/test/resources/", instrumentedMethods.get(1), true);
        assertEquals(1, serializedObjects.size());
        SerializedObject first = (SerializedObject) serializedObjects.toArray()[0];
        assertEquals(0, first.getNestedSerializedObjects().size());
    }
}
