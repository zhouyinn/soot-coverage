import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Logger {
    private static final List<String> statements = Collections.synchronizedList(new ArrayList<>());

    public static synchronized void log(String id) {
        statements.add(id);
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Logger::flush));
    }

    public static synchronized void logInt(String label, int val) {
        statements.add(label + " = " + val);
    }

    public static synchronized void logBoolean(String label, boolean val) {
        statements.add(label + " = " + val);
    }

    public static synchronized void logObject(String label, Object val) {
        statements.add(label + " = " + String.valueOf(val));
    }

    public static synchronized void flush() {
        System.out.println("Flushing " + statements.size() + " statements");
        try (FileWriter fw = new FileWriter("coverage.log", true)) {
            for (String s : statements) {
                fw.write(s + "\n");
            }
            fw.flush();
            statements.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}