import soot.*;
import soot.jimple.*;
import util.RuntimeLogUtil;
import java.util.*;
import java.util.stream.Collectors;

class LineCounter {
    int subconditionCounter = 1;
}

public class ConditionTransformer extends BodyTransformer {

    private final Map<String, Set<Integer>> linesToInstrument;

    public ConditionTransformer(Map<String, Set<Integer>> linesToInstrument) {
        this.linesToInstrument = linesToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();

        // Skip Logger or Test classes
        if (className.startsWith("Logger") || className.endsWith("Test")) return;

        System.out.println("Instrumenting CONDITIONS in: " + body.getMethod().getSignature());

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
                        bodyLines.stream().anyMatch(bodyLine -> Math.abs(bodyLine - reqLine) <= 1)
                )
                .collect(Collectors.toSet());

        if (relevantLines.isEmpty()) return;

        instrumentConditions(body, relevantLines);
    }

    private void instrumentConditions(Body body, Set<Integer> linesToProcess) {
        PatchingChain<Unit> units = body.getUnits();
        List<Unit> safeUnits = new ArrayList<>(units);
        LineCounter counter = new LineCounter();

        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");

        safeUnits.stream()
                .filter(stmt -> {
                    int line = stmt.getJavaSourceStartLineNumber();
                    return line > 0 && linesToProcess.contains(line);
                })
                .forEach(stmt -> {
                    if (stmt instanceof IfStmt) {
                        IfStmt ifStmt = (IfStmt) stmt;

                        // 1️⃣ Insert SUBCONDITION_CHECKED log
                        RuntimeLogUtil.insertSubconditionCheckedLog(
                                counter.subconditionCounter,
                                stmt,
                                units,
                                body,
                                logMethod
                        );

                        // 2️⃣ Instrument the condition (this uses your ConditionInstrumenter)
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

                        counter.subconditionCounter++;
                    }
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