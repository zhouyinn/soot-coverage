import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.util.Chain;

import java.util.*;

public class ProductCodeTransformer extends BodyTransformer {
    private final Map<String, Set<Integer>> linesToInstrument;
    private final Map<Value, Local> tempCache = new HashMap<>();
    private int tempCounter = 1;

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

        for (Iterator<Unit> it = units.snapshotIterator(); it.hasNext();) {
            Stmt stmt = (Stmt) it.next();
            int line = stmt.getJavaSourceStartLineNumber();
            if (line <= 0 || !linesToProcess.contains(line)) continue;

            if (stmt instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) stmt;
                logAssignmentExpr(assign, units, stmt, logMethod);
            }

            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                Value newCond = ConditionInstrumenter.instrument(
                        ifStmt.getCondition(), stmt, units, body, logMethod, tempCache, this::nextTemp);
                if (newCond instanceof ConditionExpr) {
                    ifStmt.setCondition((ConditionExpr) newCond);
                }
            }
        }
    }
    private int nextTemp() {
        return tempCounter++;
    }

    private Set<Integer> findInstrumentedLines(String className) {
        String classFilePath = className.replace('.', '/') + ".java";
        return linesToInstrument.entrySet().stream()
                .filter(entry -> classFilePath.contains(entry.getKey()) || entry.getKey().contains(classFilePath))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }


    private void logAssignmentExpr(AssignStmt assign, Chain<Unit> units, Unit anchor, SootMethod logMethod) {
        Value lhs = assign.getLeftOp();
        Value rhs = assign.getRightOp();

        String logText = lhs.toString() + " = " + rhs.toString();
        InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(logText));
        units.insertBefore(Jimple.v().newInvokeStmt(logExpr), anchor);
    }



}