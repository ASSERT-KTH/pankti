package se.kth.castor.pankti.instrument.plugins;

import com.thoughtworks.xstream.XStream;
import se.kth.castor.pankti.instrument.converters.*;

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
        String filePath = "../output/object-data/" + path;
        String receivingObjectFilePath = filePath + "-receiving.xml";
        String paramObjectsFilePath = filePath + "-params.xml";
        String returnedObjectFilePath = filePath + "-returned.xml";
        String invocationCountFilePath = filePath + "-count.txt";
        return new String[]{receivingObjectFilePath, paramObjectsFilePath, returnedObjectFilePath, invocationCountFilePath};
    }
}
