package se.kth.castor.pankti.instrument.plugins;

import com.thoughtworks.xstream.XStream;
import se.kth.castor.pankti.instrument.converters.*;

import java.nio.file.Files;
import java.nio.file.Paths;

public interface AdviceTemplate {
    XStream xStream = new XStream();

    static void setUpXStream() {
        xStream.registerConverter(new FileCleanableConverter());
        xStream.registerConverter(new InflaterConverter());
        xStream.registerConverter(new CleanerImplConverter());
        xStream.registerConverter(new ThreadConverter());
        xStream.registerConverter(new ThreadGroupConverter());
    }

    static String[] setUpFiles(String path) {
        try {
            String storageDir = "/tmp/pankti-object-data/";
            Files.createDirectories(Paths.get(storageDir));
            String filePath = storageDir + path;
            String receivingObjectFilePath = filePath + "-receiving.xml";
            String paramObjectsFilePath = filePath + "-params.xml";
            String returnedObjectFilePath = filePath + "-returned.xml";
            String invocationCountFilePath = filePath + "-count.txt";
            return new String[]{receivingObjectFilePath, paramObjectsFilePath, returnedObjectFilePath, invocationCountFilePath};
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        return new String[]{};
    }
}
