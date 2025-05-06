import soot.*;
import soot.tagkit.LineNumberTag;
import soot.tagkit.Tag;
import soot.toolkits.graph.ExceptionalUnitGraph;
import util.ControlFlowUtil;
import util.RuntimeLogUtil;

import java.util.*;
import java.util.stream.Collectors;

public class ExercisedLineTransformer extends BodyTransformer {

    private final Map<String, Set<Integer>> linesToInstrument;
    private static final Set<String> linesLogged = new HashSet<>();

    public ExercisedLineTransformer(Map<String, Set<Integer>> linesToInstrument) {
        this.linesToInstrument = linesToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();

        // Skip Logger or Test classes
        if (className.startsWith("Logger") || className.endsWith("Test")) return;

        System.out.println("Instrumenting EXERCISED lines in: " + body.getMethod().getSignature());

        Set<Integer> requestedLines = findInstrumentedLines(className);
        if (requestedLines == null || requestedLines.isEmpty()) return;

        // Get actual lines inside the method
        Set<Integer> bodyLines = body.getUnits().stream()
                .map(u -> u.getJavaSourceStartLineNumber())
                .filter(line -> line > 0)
                .collect(Collectors.toSet());

        // Filter only matching lines
        Set<Integer> relevantLines = requestedLines.stream()
                .filter(reqLine ->
                        bodyLines.stream().anyMatch(bodyLine -> Math.abs(bodyLine - reqLine) <= 1) // allow +/-1 offset
                )
                .collect(Collectors.toSet());

        if (relevantLines.isEmpty()) return;

        // Instrument the matched lines
        instrumentLineExercised(body, relevantLines);
    }

    private void instrumentLineExercised(Body body, Set<Integer> linesToProcess) {
        ExceptionalUnitGraph cfg = new ExceptionalUnitGraph(body);
        PatchingChain<Unit> units = body.getUnits();
        List<Unit> safeUnits = new ArrayList<>(units);

        // Map of line number -> List<Unit> that match
        Map<Integer, List<Unit>> lineToStmts = new HashMap<>();

        safeUnits.stream()
                .filter(unit -> {
                    Tag tag = unit.getTag("LineNumberTag");
                    return tag instanceof LineNumberTag &&
                            linesToProcess.contains(((LineNumberTag) tag).getLineNumber());
                })
                .forEach(unit -> {
                    int line = ((LineNumberTag) unit.getTag("LineNumberTag")).getLineNumber();
                    lineToStmts.computeIfAbsent(line, k -> new ArrayList<>()).add(unit);
                });

        String classFile = body.getMethod().getDeclaringClass().getName().replace('.', '/') + ".java";
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");

        linesToProcess.stream()
                .map(line -> new AbstractMap.SimpleEntry<>(line, ControlFlowUtil.findBestStmtUsingCPG(cfg, lineToStmts, line)))
                .peek(entry -> {
                    int line = entry.getKey();
                    Unit bestStmt = entry.getValue();
                    if (bestStmt != null) {
                        int actualLine = bestStmt.getJavaSourceStartLineNumber();
                        System.out.println("[ExercisedLineTransformer] Requested line " + line + ", found best Unit at line " + actualLine);
                    } else {
                        System.out.println("[ExercisedLineTransformer] Requested line " + line + ", but no reachable Unit found!");
                    }
                })
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> {
                    String lineKey = classFile + ":" + entry.getKey();
                    return !linesLogged.contains(lineKey);
                })
                .forEach(entry -> {
                    int line = entry.getKey();
                    Unit bestStmt = entry.getValue();
                    String lineKey = classFile + ":" + line;
                    RuntimeLogUtil.insertLineExercisedLog(bestStmt, units, classFile, line, logMethod);
                    linesLogged.add(lineKey);
                });
    }

    private Set<Integer> findInstrumentedLines(String className) {
        String classFilePath = className.replace('.', '/') + ".java";
        return linesToInstrument.entrySet().stream()
                .filter(entry -> classFilePath.contains(entry.getKey()) || entry.getKey().contains(classFilePath))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}