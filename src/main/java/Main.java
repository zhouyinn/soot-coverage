import soot.*;
import soot.options.Options;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {

    static String sootRuntime = System.getenv().getOrDefault("SOOT_RUNTIME_CLASSES", "target/classes");

    static String getFullClassPath(String modulePath) {
        return sootRuntime + ":" +
                modulePath + "/target/classes:" +
                modulePath + "/target/test-classes";
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java Main <target-project-root> <file-with-lines-to-instrument> <file-with-fields-to-instrument> [mode: jimple|class]");
            System.exit(1);
        }

        String rootProject = args[0];
        String fileWithLinesToInstrument = args[1];
        String fileWithFieldsToInstrument = args[2];

        // Optional 4th arg: mode (default: class)
        String mode = (args.length >= 4) ? args[3].toLowerCase() : "class";
        if (!mode.equals("jimple") && !mode.equals("class")) {
            System.err.println("Invalid mode: " + mode + ". Expected 'jimple' or 'class'.");
            System.exit(1);
        }

        int outputFormat = mode.equals("jimple") ? Options.output_format_jimple : Options.output_format_class;

        System.out.println(">> Instrumenting project at: " + rootProject);
        System.out.println(">> Mode: " + mode + " (output format: " + (mode.equals("jimple") ? "Jimple" : "Class files") + ")");

        // Read lines to instrument
        Map<String, Set<Integer>> linesToInstrument = readLinesFromFile(fileWithLinesToInstrument);
        System.out.println(">> Using file for specific lines to instrument: " + linesToInstrument);

        // Read fields to instrument
        Map<String, Set<String>> fieldsToInstrument = readFieldsFromFile(fileWithFieldsToInstrument);
        System.out.println(">> Using file for specific fields to instrument: " + fieldsToInstrument);

        // Find all modules (folders with pom.xml and src/)
        List<File> moduleDirs = Files.list(Paths.get(rootProject))
                .filter(Files::isDirectory)
                .map(Path::toFile)
                .filter(dir -> new File(dir, "pom.xml").exists() && new File(dir, "src").exists())
                .collect(Collectors.toList());

        // If no modules found, treat root as a single module
        if (moduleDirs.isEmpty()) {
            moduleDirs = Collections.singletonList(new File(rootProject));
        }

        // Process each module
        for (File moduleDir : moduleDirs) {
            System.out.println("\n===============================");
            System.out.println("🔎 Processing module: " + moduleDir.getName());

            String modulePath = moduleDir.getAbsolutePath();

            List<BodyTransformer> productTransformers = Arrays.asList(
                    new ExercisedLineTransformer(linesToInstrument),
                    new ConditionTransformer(linesToInstrument)
            );

            // --- Product classes ---
            instrumentClasses(
                    modulePath,
                    "target/classes",
                    mode.equals("jimple") ? "jimple-out" : "instrumented-classes",
                    productTransformers,
                    outputFormat
            );

            G.reset();

            // --- Test classes ---
            instrumentClasses(
                    modulePath,
                    "target/test-classes",
                    mode.equals("jimple") ? "jimple-test-out" : "instrumented-test-classes",
                    Collections.singletonList(new TestCodeTransformer()),
                    outputFormat
            );

            G.reset();
        }

        System.out.println("\n✅ All modules processed.");
    }

    static Map<String, Set<Integer>> readLinesFromFile(String fileWithLinesToInstrument) {
        Map<String, Set<Integer>> linesMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileWithLinesToInstrument))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length != 2) continue;
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

    private static Map<String, Set<String>> readFieldsFromFile(String filePath) {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            System.out.println("⚠️ Fields file not found: " + filePath + " (returning empty map)");
            return Collections.emptyMap();
        }

        try {
            return Files.lines(path)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(line -> line.split("\\.", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.groupingBy(
                            parts -> parts[0],
                            Collectors.mapping(parts -> parts[1], Collectors.toSet())
                    ));
        } catch (IOException e) {
            System.out.println("⚠️ Error reading file: " + filePath + " (returning empty map)");
            return Collections.emptyMap();
        }
    }

    private static void instrumentClasses(
            String modulePath,
            String inputSubdir,
            String outputSubdir,
            List<BodyTransformer> transformers,
            int outputFormat
    ) {
        String inputDir = modulePath + "/" + inputSubdir;
        String outputDir = modulePath + "/" + outputSubdir;
        String fullClasspath = getFullClassPath(modulePath);

        System.out.println(">>> Processing: " + inputDir);
        System.out.println(">>> Output to: " + outputDir);
        System.out.println(">>> Full classpath: " + fullClasspath);

        File inputDirFile = new File(inputDir);
        if (!inputDirFile.exists()) {
            System.err.println("⚠️ Skipping instrumentation: " + inputDir + " does not exist.");
            return;
        }

        G.reset(); // Reset Soot

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

        // Register transformers
        for (BodyTransformer transformer : transformers) {
            String name = transformer.getClass().getSimpleName();
            PackManager.v().getPack("jtp").add(new Transform("jtp." + name, transformer));
        }

        Scene.v().loadNecessaryClasses();

        System.out.println("=== Classes loaded by Soot ===");
        Scene.v().getApplicationClasses().stream()
                .map(SootClass::getName)
                .forEach(name -> System.out.println("  " + name));

        PackManager.v().runPacks();
        PackManager.v().writeOutput();
    }
}