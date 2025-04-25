import soot.*;
import soot.jimple.*;

import java.util.*;

public class ProductCodeTransformer extends BodyTransformer {
    private final Map<String, Set<Integer>> linesToInstrument;
    public ProductCodeTransformer(Map<String, Set<Integer>> linesToInstrument) {
        this.linesToInstrument = linesToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();
        String methodName = body.getMethod().getName();
        if (className.startsWith("Logger") || className.endsWith("Test") || methodName.equals("<clinit>")) return;

        System.out.println("Instrumenting: " + body.getMethod().getSignature());

        PatchingChain<Unit> units = body.getUnits();
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");
        Set<Integer> linesToProcess = findInstrumentedLines(className);

        if (linesToProcess == null) return;

        List<Unit> safeUnits = new ArrayList<>(body.getUnits());
        safeUnits.stream()
                .filter(stmt -> stmt instanceof Stmt)
                .filter(stmt -> {
                    int line = stmt.getJavaSourceStartLineNumber();
                    return line > 0 && linesToProcess.contains(line);
                })
                .forEach(stmt -> {
                    if (stmt instanceof AssignStmt) {
                        AssignStmt assign = (AssignStmt) stmt;
                        String logText = assign.getLeftOp() + " = " + assign.getRightOp();
                        InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(logText));
                        Unit logStmt = Jimple.v().newInvokeStmt(logExpr);
                        body.getUnits().insertBefore(logStmt, stmt);
                    }

                    if (stmt instanceof IfStmt) {
                        IfStmt ifStmt = (IfStmt) stmt;
                        Value newCond = ConditionInstrumenter.instrument(
                                ifStmt.getCondition(), stmt, units, body, logMethod);
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



}