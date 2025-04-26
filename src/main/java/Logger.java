import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Logger {
    private static final List<String> statements = Collections.synchronizedList(new ArrayList<>());
    private static final int FLUSH_THRESHOLD = 1000; // Flush every 1000 logs

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Logger::flush));
    }

    public static void log(String id) {
        synchronized (statements) {
            statements.add(id);
            if (statements.size() >= FLUSH_THRESHOLD) {
                flush();
            }
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

    // Overloaded log methods for primitives
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