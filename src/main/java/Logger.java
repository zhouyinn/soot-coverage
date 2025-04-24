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
        flush();
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Logger::flush));
    }

    public static synchronized void flush() {
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

    public static void log(String key, int value) {
        System.out.println(key + " = " + value);
    }

    public static void log(String key, boolean value) {
        System.out.println(key + " = " + value);
    }

    public static void log(String key, Object value) {
        System.out.println(key + " = " + value);
    }

}