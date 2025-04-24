import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.util.Chain;

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

        for (Iterator<Unit> it = units.snapshotIterator(); it.hasNext();) {
            Stmt stmt = (Stmt) it.next();
            int line = stmt.getJavaSourceStartLineNumber();
            if (line <= 0 || !linesToProcess.contains(line)) continue;

            logStatementAssignment(stmt, body, units, logMethod);
            handleMethodCallAssignments(stmt, units, body, logMethod);
            logStatementMetadata(stmt, body, line, units, logMethod);
            instrumentIfCondition(stmt, units, body, logMethod);
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

    private void logStatementAssignment(Stmt stmt, Body body, Chain<Unit> units, SootMethod logMethod) {
        if (!(stmt instanceof JAssignStmt)) return;

        JAssignStmt assign = (JAssignStmt) stmt;
        Value left = assign.getLeftOp();
        Value right = assign.getRightOp();

        if (left instanceof Local && !(right instanceof InvokeExpr)) {
            boolean isArrayAccess = right instanceof ArrayRef;
            String msg = isArrayAccess
                    ? "assign (array access) " + left + " = " + right
                    : "assign " + left + " = " + right;

            List<Value> args = Collections.singletonList(StringConstant.v(msg));
            units.insertBefore(
                    Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), args)),
                    stmt
            );
        }
    }

    private void handleMethodCallAssignments(Stmt stmt, Chain<Unit> units, Body body, SootMethod logMethod) {
        if (!(stmt instanceof AssignStmt)) return;

        AssignStmt assign = (AssignStmt) stmt;
        Value lhs = assign.getLeftOp();
        Value rhs = assign.getRightOp();

        if (rhs instanceof InvokeExpr) {
            Local temp = InstrumentationUtil.addTempLocal(body, rhs.getType());
            units.insertBefore(Jimple.v().newAssignStmt(temp, rhs), stmt);

            String exprLog = temp.getName() + " = " + rhs;
            units.insertBefore(Jimple.v().newInvokeStmt(
                    Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(exprLog))
            ), stmt);

            InstrumentationUtil.insertRuntimeLog(temp, units, stmt, body, logMethod);
            assign.setRightOp(temp);
        }
    }

    private void logStatementMetadata(Stmt stmt, Body body, int line, Chain<Unit> units, SootMethod logMethod) {
        String extra = stmt.getClass().getSimpleName() + " " + stmt;
        String id = body.getMethod().getSignature() + ":" + line + " → " + extra;
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(id))
        ), stmt);
    }

    private void instrumentIfCondition(Stmt stmt, Chain<Unit> units, Body body, SootMethod logMethod) {
        if (!(stmt instanceof IfStmt)) return;

        IfStmt ifStmt = (IfStmt) stmt;
        Value cond = ifStmt.getCondition();
        Value rewritten = ConditionInstrumenter.instrument(cond, stmt, units, body, logMethod);

        if (rewritten instanceof ConditionExpr) {
            ifStmt.setCondition((ConditionExpr) rewritten);
        }
    }




}