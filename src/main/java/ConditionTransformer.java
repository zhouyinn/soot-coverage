import soot.*;
import soot.jimple.*;
import util.RuntimeLogUtil;

import java.util.*;
import java.util.stream.Collectors;

public class ConditionTransformer extends BodyTransformer {

    private final Map<String, Set<Integer>> linesToInstrument;

    public ConditionTransformer(Map<String, Set<Integer>> linesToInstrument) {
        this.linesToInstrument = linesToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();

        // Skip Logger or Test classes
        if (className.startsWith("Logger") || className.endsWith("Test")) {
            return;
        }

        System.out.println("🔧 Instrumenting CONDITIONS in: " + body.getMethod().getSignature());

        Set<Integer> requestedLines = findInstrumentedLines(className);
        if (requestedLines == null || requestedLines.isEmpty()) {
            return;
        }

        // Get actual lines inside the method
        Set<Integer> bodyLines = body.getUnits().stream()
                .map(Unit::getJavaSourceStartLineNumber)
                .filter(line -> line > 0)
                .collect(Collectors.toSet());

        // Filter only matching lines
        Set<Integer> relevantLines = requestedLines.stream()
                .filter(reqLine -> bodyLines.stream().anyMatch(bodyLine -> Math.abs(bodyLine - reqLine) <= 1))
                .collect(Collectors.toSet());

        if (relevantLines.isEmpty()) {
            return;
        }

        instrumentConditions(body, relevantLines);
    }

    private void instrumentConditions(Body body, Set<Integer> linesToProcess) {
        PatchingChain<Unit> units = body.getUnits();
        List<Unit> safeUnits = new ArrayList<>(units);

        // Track subcondition counters per line number
        Map<Integer, Integer> subconditionCounterMap = new HashMap<>();

        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");

        for (Unit stmt : safeUnits) {
            int line = stmt.getJavaSourceStartLineNumber();
            if (line > 0 && linesToProcess.contains(line) && stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;

                // Get or initialize the counter for this line
                int subCounter = subconditionCounterMap.getOrDefault(line, 1);

                // Log SUBCONDITION_CHECKED
                RuntimeLogUtil.insertSubconditionCheckedLog(
                        subCounter,
                        stmt,
                        units,
                        body,
                        logMethod
                );

                // Instrument the condition (e.g., add logs and wrap it)
                Value newCond = ConditionInstrumenter.instrument(
                        ifStmt.getCondition(),
                        stmt,
                        units,
                        body,
                        logMethod
                );

                if (newCond instanceof ConditionExpr) {
                    ifStmt.setCondition((ConditionExpr) newCond);
                }

                // Increment the counter for this line
                subconditionCounterMap.put(line, subCounter + 1);
            }
        }
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