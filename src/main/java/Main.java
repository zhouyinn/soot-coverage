import soot.*;
import soot.options.Options;

import java.io.File;
import java.util.*;

public class Main {
//    static String rtJar = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/jre/lib/rt.jar";
    static String sootRuntime = "target/classes";

    static String getFullClassPath(String targetProject) {
        return sootRuntime + ":" +
                targetProject + "/target/classes:" +
                targetProject + "/target/test-classes";
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <target-maven-project-path>");
            System.exit(1);
        }

        String targetProject = args[0];
        System.out.println(">> Instrumenting project at: " + targetProject);

        // Generate instrumented .class files
        instrumentClasses(targetProject, "target/classes", "instrumented-classes", new ProductCodeTransformer(), Options.output_format_class);
        G.reset();
        instrumentClasses(targetProject, "target/test-classes", "instrumented-test-classes", new TestCodeTransformer(), Options.output_format_class);
        G.reset();

        // Generate Jimple output
        instrumentClasses(targetProject, "target/classes", "jimple-out", new ProductCodeTransformer(), Options.output_format_jimple);
        G.reset();
        instrumentClasses(targetProject, "target/test-classes", "jimple-test-out", new TestCodeTransformer(), Options.output_format_jimple);
    }

    private static void instrumentClasses(String targetProjectPath, String inputSubdir, String outputSubdir, BodyTransformer transformer, int outputFormat) {
        String inputDir = targetProjectPath + "/" + inputSubdir;
        String outputDir = targetProjectPath + "/" + outputSubdir;
        String fullClasspath = getFullClassPath(targetProjectPath);

        System.out.println(">>> Processing: " + inputDir);
        System.out.println(">>> Output to: " + outputDir);
        System.out.println(">>> Full classpath: " + fullClasspath);

        File inputDirFile = new File(inputDir);
        if (!inputDirFile.exists()) {
            System.err.println("⚠️ Skipping instrumentation: " + inputDir + " does not exist.");
            return;
        }

        G.reset(); // Reset for each run

        // Configure Soot
        Options.v().set_prepend_classpath(true);
        Options.v().set_soot_classpath(fullClasspath);
        Scene.v().loadClassAndSupport("Logger").setLibraryClass();
        Options.v().set_process_dir(Collections.singletonList(inputDir));
        Options.v().set_output_dir(outputDir);
        Options.v().set_output_format(outputFormat);
        Options.v().set_whole_program(false);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_include_all(true);

        // Register transformer
        PackManager.v().getPack("jtp").add(new Transform("jtp.instrument", transformer));

        // Load classes
        Scene.v().loadNecessaryClasses();

        // Debug print
        System.out.println("=== Classes loaded by Soot ===");
        for (SootClass cls : Scene.v().getApplicationClasses()) {
            System.out.println("  " + cls.getName());
        }

        // Run Soot and write output
        PackManager.v().runPacks();
        PackManager.v().writeOutput();
    }

}