import soot.*;
import soot.options.Options;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
//    static String rtJar = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/jre/lib/rt.jar";
    static String sootRuntime = System.getenv().getOrDefault("SOOT_RUNTIME_CLASSES", "target/classes");

    static String getFullClassPath(String targetProject) {
        return sootRuntime + ":" +
                targetProject + "/target/classes:" +
                targetProject + "/target/test-classes";
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java Main <target-maven-project-path> <file-with-lines-to-instrument> <file-with-fields-to-instrument>");
            System.exit(1);
        }


        String targetProject = args[0];
        String fileWithLinesToInstrument = args[1];
        String fileWithFieldsToInstrument = args[2];
        String sootRuntime = args.length >= 4 ? args[3] : "target/classes";

        String classpath = sootRuntime + ":" +
                targetProject + "/target/classes:" +
                targetProject + "/target/test-classes";
        Options.v().set_soot_classpath(classpath);

        System.out.println(">> Instrumenting project at: " + targetProject);

        // Read lines to instrument
        Map<String, Set<Integer>> linesToInstrument = readLinesFromFile(fileWithLinesToInstrument);
        System.out.println(">> Using file for specific lines to instrument: " + linesToInstrument.toString());

        // Read fields to instrument
        Map<String, Set<String>> fieldsToInstrument = readFieldsFromFile(fileWithFieldsToInstrument);
        System.out.println(">> Using file for specific fields to instrument: " + fieldsToInstrument);


        //  Generate instrumented .class files
        instrumentClasses(targetProject, "target/classes", "instrumented-classes", new ProductCodeTransformer(linesToInstrument, fieldsToInstrument), Options.output_format_class);
        G.reset();
        instrumentClasses(targetProject, "target/test-classes", "instrumented-test-classes", new TestCodeTransformer(), Options.output_format_class);
        G.reset();

        // Generate Jimple output
        instrumentClasses(targetProject, "target/classes", "jimple-out", new ProductCodeTransformer(linesToInstrument, fieldsToInstrument), Options.output_format_jimple);
        G.reset();
        instrumentClasses(targetProject, "target/test-classes", "jimple-test-out", new TestCodeTransformer(), Options.output_format_jimple);
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

                Arrays.stream(lineNumbers)
                        .forEach(lineNumber -> {
                            if (lineNumber.contains("-")) {
                                String[] rangeParts = lineNumber.split("-");
                                int start = Integer.parseInt(rangeParts[0].trim());
                                int end = Integer.parseInt(rangeParts[1].trim());
                                IntStream.rangeClosed(start, end)
                                        .forEach(lineSet::add);
                            } else {
                                lineSet.add(Integer.parseInt(lineNumber.trim()));
                            }
                        });

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

    private static Map<String, Set<String>> readFieldsFromFile(String filePath) throws IOException {
        return Files.lines(Paths.get(filePath))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\.", 2)) // Split into [ClassName, FieldName]
                .filter(parts -> parts.length == 2)
                .collect(Collectors.groupingBy(
                        parts -> parts[0],                      // Class name (e.g., Hello, Foo)
                        Collectors.mapping(parts -> parts[1], Collectors.toSet()) // Field names (e.g., CONSTANT, name)
                ));
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
        Options.v().set_src_prec(Options.src_prec_only_class);
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
        Scene.v().getApplicationClasses().stream()
                .map(SootClass::getName)
                .forEach(name -> System.out.println("  " + name));

        // Run Soot and write output
        PackManager.v().runPacks();
        PackManager.v().writeOutput();
    }

}