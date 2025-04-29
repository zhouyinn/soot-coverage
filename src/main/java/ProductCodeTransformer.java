import soot.*;
import soot.jimple.*;
import soot.tagkit.Tag;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.tagkit.LineNumberTag;
import util.ControlFlowUtil;
import util.RuntimeLogUtil;

import java.util.*;
import java.util.stream.Collectors;

class LineCounter {
    int subconditionCounter = 1;
}

public class ProductCodeTransformer extends BodyTransformer {
    private final Map<String, Set<Integer>> linesToInstrument;
    private final Map<String, Set<String>> fieldsToInstrument;

    private static final Set<String> linesLogged = new HashSet<>();
    public ProductCodeTransformer(Map<String, Set<Integer>> linesToInstrument, Map<String, Set<String>> fieldsToInstrument) {
        this.linesToInstrument = linesToInstrument;
        this.fieldsToInstrument = fieldsToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();
        String methodName = body.getMethod().getName();
        if (className.startsWith("Logger") || className.endsWith("Test") || methodName.equals("<init>")) return;

        System.out.println("Instrumenting: " + body.getMethod().getSignature());

        Set<Integer> allRequestedLines = findInstrumentedLines(className);
        Set<String> fieldsToMonitor = findFieldSignatures(
                body.getMethod().getDeclaringClass(),
                body.getMethod().getDeclaringClass().getName()
        );

        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");

        instrumentFieldAccesses(body, fieldsToMonitor, logMethod);

        if (allRequestedLines == null) return;

        // 1. Get actual lines inside this method
        Set<Integer> bodyLines = body.getUnits().stream()
                .map(u -> u.getJavaSourceStartLineNumber())
                .filter(line -> line > 0)
                .collect(Collectors.toSet());

        // 2. Keep only matching lines
        Set<Integer> relevantLines = allRequestedLines.stream()
                .filter(reqLine ->
                        bodyLines.stream().anyMatch(bodyLine -> Math.abs(bodyLine - reqLine) <= 1) // CAUTION: allow 0 or 1 line off
                )
                .collect(Collectors.toSet());

        if (relevantLines.isEmpty()) return;

        instrumentLineExercised(body, relevantLines, logMethod);
        instrumentConditions(body, relevantLines, logMethod);
    }

    private void instrumentConditions(Body body, Set<Integer> linesToProcess, SootMethod logMethod) {
        List<Unit> safeUnits = new ArrayList<>(body.getUnits());
        LineCounter counter = new LineCounter();

        safeUnits.stream()
                .filter(stmt -> {
                    int line = stmt.getJavaSourceStartLineNumber();
                    return line > 0 && linesToProcess.contains(line);
                })
                .forEach(stmt -> {
                    if (stmt instanceof IfStmt) {
                        IfStmt ifStmt = (IfStmt) stmt;

                        // 1. Insert SUBCONDITION_CHECKED log. Log → Evaluate condition
                        RuntimeLogUtil.insertSubconditionCheckedLog(counter.subconditionCounter, stmt, body.getUnits(), body, logMethod);

                        // 2. Instrument the condition
                        Value newCond = ConditionInstrumenter.instrument(
                                ifStmt.getCondition(), stmt, body.getUnits(), body, logMethod
                        );
                        if (newCond instanceof ConditionExpr) {
                            ifStmt.setCondition((ConditionExpr) newCond);
                        }

                        // 3. Increment subconditionCounter
                        counter.subconditionCounter++;
                    }
                });
    }

    private void instrumentLineExercised(Body body, Set<Integer> linesToProcess, SootMethod logMethod) {
        ExceptionalUnitGraph cfg = new ExceptionalUnitGraph(body);
        PatchingChain<Unit> units = body.getUnits();
        List<Unit> safeUnits = new ArrayList<>(units);

        Map<Integer, List<Unit>> lineToStmts = new HashMap<>();

        safeUnits.stream()
                .filter(unit -> {
                    Tag tag = unit.getTag("LineNumberTag");
                    return tag instanceof LineNumberTag && linesToProcess.contains(((LineNumberTag) tag).getLineNumber());
                })
                .forEach(unit -> {
                    int line = ((LineNumberTag) unit.getTag("LineNumberTag")).getLineNumber();
                    lineToStmts.computeIfAbsent(line, k -> new ArrayList<>()).add(unit);
                });

        String classFile = body.getMethod().getDeclaringClass().getName() + ".java";

        linesToProcess.stream()
                .map(line -> new AbstractMap.SimpleEntry<>(line, ControlFlowUtil.findBestStmtUsingCPG(cfg, lineToStmts, line)))
//                .peek(entry -> {
//                    int line = entry.getKey();
//                    Unit bestStmt = entry.getValue();
//                    if (bestStmt != null) {
//                        int actualLine = bestStmt.getJavaSourceStartLineNumber();
//                        System.out.println("[Instrumentation] Requested line " + line + ", found best Unit at line " + actualLine);
//                    } else {
//                        System.out.println("[Instrumentation] Requested line " + line + ", but no reachable Unit found!");
//                    }
//                })
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

    public static Set<String> findFieldSignatures(SootClass klass, String className) {
        return klass.getFields().stream()
                .filter(f -> f.getDeclaringClass().getName().equals(className))
                .map(f -> f.getDeclaringClass().getName() + "." + f.getName()) // Hello.CONSTANT
                .collect(Collectors.toSet());
    }

    private void instrumentFieldAccesses(Body body, Set<String> fieldsToMonitor, SootMethod logMethod) {
        PatchingChain<Unit> units = body.getUnits();
        List<Unit> safeUnits = new ArrayList<>(units);

        safeUnits.stream()
                .filter(u -> u instanceof AssignStmt)
                .map(u -> (AssignStmt) u)
                .filter(assign -> {
                    Value rightOp = assign.getRightOp();
                    return (rightOp instanceof StaticFieldRef || rightOp instanceof InstanceFieldRef);
                })
                .forEach(assign -> {
                    Value rightOp = assign.getRightOp();
                    SootField field = rightOp instanceof StaticFieldRef
                            ? ((StaticFieldRef) rightOp).getField()
                            : ((InstanceFieldRef) rightOp).getField();

                    String shortSignature = field.getDeclaringClass().getName() + "." + field.getName();

                    if (fieldsToMonitor.contains(shortSignature)) {
//                        System.out.println(">>> Matched access to field: " + shortSignature);
                        RuntimeLogUtil.insertFieldAccessLog(field, assign, units, logMethod);
                    }
                });
    }

}