import soot.*;
import soot.jimple.*;

import java.util.*;

public class ProductCodeTransformer extends BodyTransformer {
    private final Map<String, Set<Integer>> linesToInstrument;
    private static final Set<Integer> linesLogged = new HashSet<>();
    public ProductCodeTransformer(Map<String, Set<Integer>> linesToInstrument) {
        this.linesToInstrument = linesToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();
        String methodName = body.getMethod().getName();
        if (className.startsWith("Logger") || className.endsWith("Test") || methodName.equals("<clinit>")) return;

        System.out.println("Instrumenting: " + body.getMethod().getSignature());

        Set<Integer> linesToProcess = findInstrumentedLines(className);

        if (linesToProcess == null) return;

        List<Unit> safeUnits = new ArrayList<>(body.getUnits());
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");
        safeUnits.stream()
                .filter(stmt -> {
                    int line = stmt.getJavaSourceStartLineNumber();
                    return line > 0 && linesToProcess.contains(line);
                })
                .forEach(stmt -> {
                    int line = stmt.getJavaSourceStartLineNumber();
                    if (!linesLogged.contains(line)) {
                        insertLineExercisedLog(stmt, body.getUnits(), body, logMethod);
                        linesLogged.add(line);
                    }

                    if (stmt instanceof IfStmt) {
                        IfStmt ifStmt = (IfStmt) stmt;
                        Value newCond = ConditionInstrumenter.instrument(
                                ifStmt.getCondition(), stmt, body.getUnits(), body, logMethod);
                        if (newCond instanceof ConditionExpr) {
                            ifStmt.setCondition((ConditionExpr) newCond);
                        }
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
        String sourceFile = body.getMethod().getDeclaringClass().getShortName() + ".java"; // or add package if you want
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