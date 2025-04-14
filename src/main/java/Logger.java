import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class Logger {
    private static final List<String> statements = Collections.synchronizedList(new ArrayList<>());

    public static synchronized void log(String id) {
        statements.add(id);
    }

    public static void dump() {
        try (FileWriter fw = new FileWriter("coverage.log")) {
            for (String stmt : statements) {
                fw.write(stmt + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Logger::dump));
    }
}