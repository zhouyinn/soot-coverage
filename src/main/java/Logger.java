import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Logger {
    // Collect logs for current test case
    private static final List<String> logsThisTestCase = Collections.synchronizedList(new ArrayList<>());

    // Subcondition suppression inside a test case
    private static final Set<String> seenSubconditions = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> seenLines = Collections.synchronizedSet(new HashSet<>());

    // Overall batch writing
    private static final List<String> statements = Collections.synchronizedList(new ArrayList<>());
    private static final int FLUSH_THRESHOLD = 1000;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Logger::flush));
    }

    public static void log(String id) {
        synchronized (logsThisTestCase) {
            if (id.contains("\"event\":\"SUBCONDITION_CHECKED\"")) {
                String key = extractSubconditionKey(id);  // file + line + index
                if (seenSubconditions.contains(key)) {
                    return; // already logged this exact subcondition
                }
                seenSubconditions.add(key);
            } else if (id.contains("\"event\":\"EXERCISED\"")) {
                String key = extractLineKey(id);  // file + line only
                if (seenLines.contains(key)) {
                    return; // already logged this line executed
                }
                seenLines.add(key);
            }
            logsThisTestCase.add(id);
        }
    }

    public static void flush() {
        synchronized (statements) {
            if (statements.isEmpty()) return;
            try (FileWriter fw = new FileWriter("coverage.log", true);
                 PrintWriter pw = new PrintWriter(fw)) {
                for (String s : statements) {
                    pw.println(s);
                }
                statements.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void flushLogs() {
        synchronized (logsThisTestCase) {
            if (logsThisTestCase.isEmpty()) return;
            synchronized (statements) {
                statements.addAll(logsThisTestCase);
                logsThisTestCase.clear();
            }
            if (statements.size() >= FLUSH_THRESHOLD) {
                flush();
            }
        }
    }

    public static void resetForNewTestCase() {
        synchronized (logsThisTestCase) {
            logsThisTestCase.clear();
            seenSubconditions.clear();
            seenLines.clear();
        }
    }

    // Helpers to extract keys
    private static String extractSubconditionKey(String message) {
        Map<String, String> map = parseSimpleJsonToMap(message);
        String file = String.valueOf(map.getOrDefault("file", ""));
        String line = String.valueOf(map.getOrDefault("line", ""));
        String index = String.valueOf(map.getOrDefault("index", ""));
        return file + ":" + line + ":" + index;
    }

    private static String extractLineKey(String message) {
        Map<String, String> map = parseSimpleJsonToMap(message);
        String file = String.valueOf(map.getOrDefault("file", ""));
        String line = String.valueOf(map.getOrDefault("line", ""));
        return file + ":" + line;
    }

    static Map<String, String> parseSimpleJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return map;
        }

        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1);
        }
        if (json.endsWith("}")) {
            json = json.substring(0, json.length() - 1);
        }

        String[] entries = json.split(",");
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            int colon = entry.indexOf(':');
            if (colon == -1) continue; // invalid entry

            String rawKey = entry.substring(0, colon).trim();
            String rawValue = entry.substring(colon + 1).trim();

            String key = stripQuotes(rawKey);
            String value = stripQuotes(rawValue);

            map.put(key, value);
        }
        return map;
    }

    private static String stripQuotes(String str) {
        str = str.trim();
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }



    // Overloaded log methods
    public static void log(String key, int value) {
        log(key + " = " + value);
    }

    public static void log(String key, boolean value) {
        log(key + " = " + value);
    }

    public static void log(String key, long value) {
        log(key + " = " + value);
    }

    public static void log(String key, float value) {
        log(key + " = " + value);
    }

    public static void log(String key, double value) {
        log(key + " = " + value);
    }

    public static void log(String key, char value) {
        log(key + " = " + value);
    }

    public static void log(String key, Object value) {
        log(key + " = " + value);
    }
}