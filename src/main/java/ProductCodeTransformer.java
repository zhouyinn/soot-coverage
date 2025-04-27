import soot.*;
import soot.jimple.*;

import java.util.*;

class LineCounter {
    int currentLine = -1;
    int subconditionCounter = 1;
}

public class ProductCodeTransformer extends BodyTransformer {
    private final Map<String, Set<Integer>> linesToInstrument;
    private static final Set<String> linesLogged = new HashSet<>();
    public ProductCodeTransformer(Map<String, Set<Integer>> linesToInstrument) {
        this.linesToInstrument = linesToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();
        String methodName = body.getMethod().getName();
        String classFile = className + ".java";
        if (className.startsWith("Logger") || className.endsWith("Test") || methodName.equals("<clinit>")) return;

        System.out.println("Instrumenting: " + body.getMethod().getSignature());

        Set<Integer> linesToProcess = findInstrumentedLines(className);

        if (linesToProcess == null) return;

        List<Unit> safeUnits = new ArrayList<>(body.getUnits());
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");
        LineCounter counter = new LineCounter();
        safeUnits.stream()
                .filter(stmt -> {
                    int line = stmt.getJavaSourceStartLineNumber();
                    return line > 0 && linesToProcess.contains(line);
                })
                .forEach(stmt -> {
                    int line = stmt.getJavaSourceStartLineNumber();
                    if (counter.currentLine != line) {
                        counter.currentLine = line;
                        counter.subconditionCounter = 1;
                    }
                    String lineKey = classFile + ":" + line;
                    if (!linesLogged.contains(lineKey)) {
                        insertLineExercisedLog(stmt, body.getUnits(), body, logMethod);
                        linesLogged.add(lineKey);
                    }

                    if (stmt instanceof IfStmt) {
                        IfStmt ifStmt = (IfStmt) stmt;

                        // 1. Insert SUBCONDITION_CHECKED log. Log → Evaluate condition
                        ConditionInstrumenter.insertSubconditionCheckedLog(counter.subconditionCounter, stmt, body.getUnits(), body, logMethod);

                        // 2. Instrument the condition
                        Value newCond = ConditionInstrumenter.instrument(
                                ifStmt.getCondition(), stmt, body.getUnits(), body, logMethod
                        );
                        if (newCond instanceof ConditionExpr) {
                            ifStmt.setCondition((ConditionExpr) newCond);
                        }

                        // 5. Increment subconditionCounter
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

    private static void insertLineExercisedLog(Unit stmt, PatchingChain<Unit> units, Body body, SootMethod logMethod) {
        String sourceFile = body.getMethod().getDeclaringClass().getName().replace('.', '/') + ".java";
        int line = stmt.getJavaSourceStartLineNumber();
        String message = "{\"event\":\"EXERCISED\",\"file\":\"" + sourceFile + "\",\"line\":\"" + line + "\"}";
        InvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(
                logMethod.makeRef(),
                StringConstant.v(message)
        );

        InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(invokeExpr);
        units.insertBefore(invokeStmt, stmt);
    }



}