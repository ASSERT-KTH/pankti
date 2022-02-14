package se.kth.castor.pankti.generate.parsers;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import se.kth.castor.pankti.generate.data.InstrumentedMethod;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CSVFileParser {
    static final String csvParentFQNField = "parent-FQN";
    static final String csvMethodNameField = "method-name";
    static final String csvParamListField = "param-list";
    static final String csvReturnTypeField = "return-type";
    static final String csvVisibilityField = "visibility";
    static final String csvMockableInvocationsField = "has-mockable-invocations";
    static final String csvNestedMethodInvocationsField = "nested-invocations";

    public static List<InstrumentedMethod> parseCSVFile(String filePath) {
        List<InstrumentedMethod> instrumentedMethods = new ArrayList<>();
        try {
            Reader in = new FileReader(filePath);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
            for (CSVRecord record : records) {
                String parentFQN = record.get(csvParentFQNField);
                String methodName = record.get(csvMethodNameField);
                String params = record.get(csvParamListField);
                String returnType = record.get(csvReturnTypeField);
                String visibility = record.get(csvVisibilityField);
                boolean hasMockableInvocations = record.get(csvMockableInvocationsField)
                        .equalsIgnoreCase("true");
                String nestedMethodMap = record.get(csvNestedMethodInvocationsField);

                List<String> paramList = new ArrayList<>();
                if (!params.isEmpty()) {
                    params = params.replaceAll("\\s", "");
                    paramList = new ArrayList<>(Arrays.asList(params.split(",")));
                }

                instrumentedMethods.add(new InstrumentedMethod(parentFQN, methodName,
                        paramList, returnType, visibility, hasMockableInvocations,
                        nestedMethodMap));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(instrumentedMethods);
        return instrumentedMethods;
    }
}
