import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

public class MainTest {

    @Test
    public void testReadLinesFromFile() throws IOException {
        // Step 1: Create a temporary file and write the test data to it
        File tempFile = File.createTempFile("test", ".txt");
        tempFile.deleteOnExit(); // Ensure the temp file is deleted after the test

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.write("com/puppycrawl/tools/checkstyle/checks/imports/IllegalImportCheck.java:457,459,461\n");
            writer.write("com/puppycrawl/tools/checkstyle/checks/regex/IllegalRegexCheck.java:58\n");
            writer.write("com/puppycrawl/tools/checkstyle/checks/regex/IllegalRegexCheck.java:100-102\n");
        }

        // Step 2: Call the method we want to test
        Map<String, Set<Integer>> linesMap = Main.readLinesFromFile(tempFile.getAbsolutePath());

        // Step 3: Assertions
        assertNotNull("The map should not be null", linesMap);
        assertEquals("The map should contain 2 files", 2, linesMap.size());

        // Test file 1
        Set<Integer> firstFileLines = linesMap.get("com/puppycrawl/tools/checkstyle/checks/imports/IllegalImportCheck.java");
        assertNotNull("The line set for the first file should not be null", firstFileLines);
        assertEquals("The first file should have 3 lines", 3, firstFileLines.size());
        assertTrue("The set should contain line 457", firstFileLines.contains(457));
        assertTrue("The set should contain line 459", firstFileLines.contains(459));
        assertTrue("The set should contain line 461", firstFileLines.contains(461));

        // Test file 2
        Set<Integer> secondFileLines = linesMap.get("com/puppycrawl/tools/checkstyle/checks/regex/IllegalRegexCheck.java");
        assertNotNull("The line set for the second file should not be null", secondFileLines);
        assertEquals("The second file should have 4 lines", 4, secondFileLines.size());
        assertTrue("The set should contain line 58", secondFileLines.contains(58));
        assertTrue("The set should contain line 100", secondFileLines.contains(100));
        assertTrue("The set should contain line 101", secondFileLines.contains(101));
        assertTrue("The set should contain line 102", secondFileLines.contains(102));
    }
}