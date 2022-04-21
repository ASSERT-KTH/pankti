package se.kth.castor.pankti.extract.reporter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NestedMethodAnalysis {
    private static final String[] HEADERS = {"method-path", "m-loc", "invocation",
            "field/param", "primitive-params", "primitive-return", "n-loc",
            "non-static", "mockable", "category"};

    static List<String> methodIds = new ArrayList<>();
    static List<Integer> methodLocs = new ArrayList<>();
    static List<String> invocations = new ArrayList<>();
    static List<Boolean> c1 = new ArrayList<>();
    static List<Boolean> c2 = new ArrayList<>();
    static List<Boolean> c3 = new ArrayList<>();
    static List<Integer> c4 = new ArrayList<>();
    static List<Boolean> c5 = new ArrayList<>();
    static List<MockableCategory> categoryList = new ArrayList<>();

    public void generateMockabilityReport(String methodId,
                                          int methodLoc,
                                          String invocation,
                                          boolean invocationOnFieldOrParam,
                                          boolean returnsPrimitiveOrString,
                                          boolean paramsArePrimitive,
                                          int loc, boolean isNonStatic,
                                          MockableCategory category) {
        methodIds.add(methodId);
        invocations.add(invocation);
        methodLocs.add(methodLoc);
        c1.add(invocationOnFieldOrParam);
        c2.add(paramsArePrimitive);
        c3.add(returnsPrimitiveOrString);
        c4.add(loc);
        c5.add(isNonStatic);
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
                        c1.get(i) ? "C1" : "!C1",
                        c2.get(i) ? "C2" : "!C2",
                        c3.get(i) ? "C3" : "!C3",
                        c4.get(i),
                        c5.get(i) ? "C5" : "!C5",
                        (methodLocs.get(i) > 1) & c1.get(i) & c3.get(i) & (c4.get(i) != 1) & c5.get(i),
                        categoryList.get(i));
            }
        }
    }
}
