import soot.*;
import soot.jimple.*;
import java.util.Iterator;
import java.util.Map;

public class StatementCoverageTransformer extends BodyTransformer {
    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        if (body.getMethod().getDeclaringClass().getName().startsWith("Logger")) {
            return;
        }
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
        }
    }
}