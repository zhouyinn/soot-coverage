import soot.*;
import soot.options.Options;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
        if (args.length < 2) {
            System.err.println("Usage: java Main <target-maven-project-path> <file-with-lines-to-instrument>");
            System.exit(1);
        }

        String targetProject = args[0];
        String fileWithLinesToInstrument = args[1];

        System.out.println(">> Instrumenting project at: " + targetProject);

        Map<String, Set<Integer>> linesToInstrument = readLinesFromFile(fileWithLinesToInstrument);
        System.out.println(">> Using file for specific lines to instrument: " + linesToInstrument.toString());


        // Generate instrumented .class files
        instrumentClasses(targetProject, "target/classes", "instrumented-classes", new ProductCodeTransformer(linesToInstrument), Options.output_format_class);
    }

    static Map<String, Set<Integer>> readLinesFromFile(String fileWithLinesToInstrument) {
        Map<String, Set<Integer>> linesMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileWithLinesToInstrument))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                String filePath = parts[0].trim();
                String[] lineNumbers = parts[1].trim().split(",");
                Set<Integer> lineSet = new HashSet<>();

                for (String lineNumber : lineNumbers) {
                    if (lineNumber.contains("-")) {
                        String[] rangeParts = lineNumber.split("-");
                        for (int i = Integer.parseInt(rangeParts[0].trim()); i <= Integer.parseInt(rangeParts[1].trim()); i++) {
                            lineSet.add(i);
                        }
                    } else {
                        lineSet.add(Integer.parseInt(lineNumber.trim()));
                    }
                }

                linesMap.merge(filePath, lineSet, (existingSet, newSet) -> {
                    existingSet.addAll(newSet);
                    return existingSet;
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return linesMap;
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