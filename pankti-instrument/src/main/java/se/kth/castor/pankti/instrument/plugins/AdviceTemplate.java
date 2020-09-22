package se.kth.castor.pankti.instrument.plugins;

import com.thoughtworks.xstream.XStream;
import se.kth.castor.pankti.instrument.converters.*;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public interface AdviceTemplate {
    XStream xStream = new XStream();

    static void setUpXStream() {
        xStream.registerConverter(new FileCleanableConverter());
        xStream.registerConverter(new InflaterConverter());
        xStream.registerConverter(new CleanerImplConverter());
        xStream.registerConverter(new ThreadConverter());
        xStream.registerConverter(new ThreadGroupConverter());
    }

    static String setUpInvokedMethodsCSVFile(String storageDir) throws Exception {
        String[] HEADERS = {"visibility", "parent-FQN", "method-name", "param-list", "return-type",
                "local-variables", "conditionals", "multiple-statements", "loops", "parameters",
                "returns", "switches", "ifs", "static", "returns-primitives", "classification"};
        File invokedMethodsCSVFile = new File(storageDir + "invoked-methods.csv");
        if (!invokedMethodsCSVFile.exists()) {
            FileWriter myWriter = new FileWriter(invokedMethodsCSVFile);
            myWriter.write(String.join(",", HEADERS));
            myWriter.close();
        }
        return invokedMethodsCSVFile.getAbsolutePath();
    }

    static String[] setUpFiles(String path) {
        try {
            String storageDir = "/tmp/pankti-object-data/";
            Files.createDirectories(Paths.get(storageDir));
            String invokedMethodsCSVFilePath = setUpInvokedMethodsCSVFile(storageDir);
            String filePath = storageDir + path;
            String receivingObjectFilePath = filePath + "-receiving.xml";
            String paramObjectsFilePath = filePath + "-params.xml";
            String returnedObjectFilePath = filePath + "-returned.xml";
            String invocationCountFilePath = filePath + "-count.txt";
            return new String[]{receivingObjectFilePath, paramObjectsFilePath, returnedObjectFilePath, invocationCountFilePath, invokedMethodsCSVFilePath};
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        return new String[]{};
    }
}
