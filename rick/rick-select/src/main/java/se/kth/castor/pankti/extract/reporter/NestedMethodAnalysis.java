package se.kth.castor.pankti.extract.reporter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NestedMethodAnalysis {
    private static final String[] HEADERS = {"method-path", "m-loc", "invocation",
            "field/param", "primitive-return", "n-loc",
            "non-static", "mockable", "category"};

    static List<String> methodIds = new ArrayList<>();
    static List<Integer> methodLocs = new ArrayList<>();
    static List<String> invocations = new ArrayList<>();
    static List<Boolean> c1_FieldParam = new ArrayList<>();
    static List<Boolean> c2_Return = new ArrayList<>();
    static List<Integer> c3_LOC = new ArrayList<>();
    static List<Boolean> c4_NonStatic = new ArrayList<>();
    static List<MockableCategory> categoryList = new ArrayList<>();

    public void generateMockabilityReport(String methodId,
                                          int methodLoc,
                                          String invocation,
                                          boolean invocationOnFieldOrParam,
                                          boolean returnsPrimitiveOrString,
                                          int loc, boolean isNonStatic,
                                          MockableCategory category) {
        methodIds.add(methodId);
        invocations.add(invocation);
        methodLocs.add(methodLoc);
        c1_FieldParam.add(invocationOnFieldOrParam);
        c2_Return.add(returnsPrimitiveOrString);
        c3_LOC.add(loc);
        c4_NonStatic.add(isNonStatic);
        categoryList.add(category);
    }

    public static void createCSVFile() throws IOException {
        try (FileWriter out = new FileWriter("./nested-method-analysis.csv");
             CSVPrinter csvPrinter = new CSVPrinter(out, CSVFormat.DEFAULT
                     .withHeader(HEADERS));
        ) {
            for (int i = 0; i < methodIds.size(); i++) {
                csvPrinter.printRecord(
                        methodIds.get(i),
                        methodLocs.get(i),
                        invocations.get(i),
                        c1_FieldParam.get(i) ? "C1" : "!C1",
                        c2_Return.get(i) ? "C2" : "!C2",
                        c3_LOC.get(i),
                        c4_NonStatic.get(i) ? "C4" : "!C4",
                        (methodLocs.get(i) > 1) &
                                c1_FieldParam.get(i) &
                                c2_Return.get(i) &
                                (c3_LOC.get(i) != 1) &
                                c4_NonStatic.get(i),
                        categoryList.get(i));
            }
        }
    }
}
