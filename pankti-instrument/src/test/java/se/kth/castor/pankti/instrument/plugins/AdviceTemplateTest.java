package se.kth.castor.pankti.instrument.plugins;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class AdviceTemplateTest {
    static final String pathName = "methodFQN";
    static final Map<Type, String> files = AdviceTemplate.setUpFiles(pathName);
    static final Map<Type, String> filesForLibraryInvocations =
            AdviceTemplate.setUpFilesForLibraryInvocation(pathName);

    public long filterFileByExtension(String extension, Map<Type, String> fileMap) {
        return fileMap.values().stream().filter(v -> v.contains(pathName) & v.endsWith(extension)).count();
    }

    // Test that 4 xml, 2 txt, and 1 csv file are set up
    @Test
    public void testFileSetup() {
        assertEquals(7, files.size());
        assertEquals(4, filterFileByExtension(".xml", files));
        assertEquals(2, filterFileByExtension(".txt", files));
        assertEquals(1, files.values().stream().filter(v -> v.endsWith(".csv")).count());
        assertFalse(files.get(Type.INVOKED_METHODS).contains(pathName));
    }

    // Test that 1 xml file is set up
    @Test
    public void testFileSetupForLibraryInvocations() {
        assertEquals(1, filesForLibraryInvocations.size());
        assertEquals(1, filterFileByExtension(".xml", filesForLibraryInvocations));
        assertEquals(0, filesForLibraryInvocations.values().stream().filter(v -> v.endsWith(".csv")).count());
        assertTrue(filesForLibraryInvocations.get(Type.LIBRARY_METHOD_INV).contains(pathName));
    }
}
