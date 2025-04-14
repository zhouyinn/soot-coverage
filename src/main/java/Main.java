import soot.*;
import soot.options.Options;
import java.util.Collections;

public class Main {
    public static void main(String[] args) {
        runSoot("jimple-out", Options.output_format_jimple);
        G.reset();
        runSoot("instrumented-classes", Options.output_format_class);
    }

    private static void runSoot(String outDir, int format) {
        Options.v().set_output_format(format);
        Options.v().set_output_dir(outDir);
        Options.v().set_soot_classpath("target-project:target/classes");
        Options.v().set_whole_program(false);
        Options.v().set_prepend_classpath(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_allow_phantom_refs(true);

        SootClass logger = Scene.v().loadClassAndSupport("Logger");
        logger.setLibraryClass();

        PackManager.v().getPack("jtp").add(
                new Transform("jtp.coverage", new StatementCoverageTransformer())
        );

        soot.Main.main(new String[] { "Hello" });
    }
}