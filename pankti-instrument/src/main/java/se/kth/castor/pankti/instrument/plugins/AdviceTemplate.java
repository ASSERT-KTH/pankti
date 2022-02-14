package se.kth.castor.pankti.instrument.plugins;

import com.thoughtworks.xstream.XStream;
import se.kth.castor.pankti.instrument.converters.*;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public interface AdviceTemplate {
    XStream xStream = new XStream();

    static void setUpXStream() {
        xStream.registerConverter(new ClassLoaderConverter());
        xStream.registerConverter(new FileCleanableConverter());
        xStream.registerConverter(new InflaterConverter());
        xStream.registerConverter(new CleanerImplConverter());
        xStream.registerConverter(new ThreadConverter());
        xStream.registerConverter(new ThreadGroupConverter());
    }

    static String setUpInvokedMethodsCSVFile(String storageDir) throws Exception {
        String[] HEADERS = {"visibility", "parent-FQN", "method-name", "param-list", "return-type",
                "param-signature", "has-mockable-invocations", "nested-invocations"};

        File invokedMethodsCSVFile = new File(storageDir + "invoked-methods.csv");
        if (!invokedMethodsCSVFile.exists()) {
            FileWriter myWriter = new FileWriter(invokedMethodsCSVFile);
            myWriter.write(String.join(",", HEADERS));
            myWriter.close();
        }
        return invokedMethodsCSVFile.getAbsolutePath();
    }

    static Map<Type, String> setUpFiles(String path) {
        Map<Type, String> fileNameMap = new HashMap<>();
        try {
            String storageDir = "/tmp/pankti-object-data/";
            Files.createDirectories(Paths.get(storageDir));
            String invokedMethodsCSVFilePath = setUpInvokedMethodsCSVFile(storageDir);
            String filePath = storageDir + path;
            fileNameMap.put(Type.RECEIVING_PRE, filePath + "-receiving.xml");
            fileNameMap.put(Type.RECEIVING_POST, filePath + "-receiving-post.xml");
            fileNameMap.put(Type.PARAMS, filePath + "-params.xml");
            fileNameMap.put(Type.RETURNED, filePath + "-returned.xml");
            fileNameMap.put(Type.INVOCATION_COUNT, filePath + "-count.txt");
            fileNameMap.put(Type.OBJECT_PROFILE_SIZE, filePath + "-object-profile-sizes.txt");
            fileNameMap.put(Type.INVOKED_METHODS, invokedMethodsCSVFilePath);
            return fileNameMap;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        return fileNameMap;
    }
}
