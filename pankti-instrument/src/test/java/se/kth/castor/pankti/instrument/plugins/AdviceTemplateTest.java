package se.kth.castor.pankti.instrument.plugins;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class AdviceTemplateTest {
    static final String pathName = "methodFQN";
    static final Map<Type, String> files = AdviceTemplate.setUpFiles(pathName);

    public long filterFileByExtension(String extension) {
        return files.values().stream().filter(v -> v.contains(pathName) & v.endsWith(extension)).count();
    }

    // Test that 4 xml, 2 txt, and 1 csv file are set up
    @Test
    public void testFileSetup() {
        assertEquals(7, files.size());
        assertEquals(4, filterFileByExtension(".xml"));
        assertEquals(2, filterFileByExtension(".txt"));
        assertEquals(1, files.values().stream().filter(v -> v.endsWith(".csv")).count());
        assertFalse(files.get(Type.INVOKED_METHODS).contains(pathName));
    }
}
