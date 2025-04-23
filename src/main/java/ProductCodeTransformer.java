import soot.*;
import soot.jimple.*;

import java.util.Iterator;
import java.util.Map;

public class ProductCodeTransformer extends BodyTransformer {
    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();
        if (className.startsWith("Logger") || className.endsWith("Test") || body.getMethod().getName().equals("<clinit>")) return;
        System.out.println("Instrumenting: " + body.getMethod().getSignature());

        PatchingChain<Unit> units = body.getUnits();
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");

        for (Iterator<Unit> it = units.snapshotIterator(); it.hasNext();) {
            Stmt stmt = (Stmt) it.next();
            int line = stmt.getJavaSourceStartLineNumber();
            if (line <= 0) continue;

            String extra = stmt.getClass().getSimpleName() + " " + stmt;
            String id = body.getMethod().getSignature() + ":" + line + " → " + extra;
            InvokeExpr logCall = Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(id));
            units.insertBefore(Jimple.v().newInvokeStmt(logCall), stmt);

            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                Value cond = ifStmt.getCondition();
                Value rewritten = StatementInstrumentationHelper.instrumentCondition(cond, stmt, units, body, logMethod);
                if (rewritten instanceof ConditionExpr) {
                    ifStmt.setCondition((ConditionExpr) rewritten);
                }
            }
        }
    }
}