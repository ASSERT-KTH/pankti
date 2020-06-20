package se.kth.castor.pankti.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class CustomLogger {

    private static Logger LOGGER = null;

    public static Logger log(String className) {
        InputStream stream = CustomLogger.class.getClassLoader().
            getResourceAsStream("logging.properties");
        try {
            LogManager.getLogManager().readConfiguration(stream);
            LOGGER = Logger.getLogger(className);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return LOGGER;
    }
}
