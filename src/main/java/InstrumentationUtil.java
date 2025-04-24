import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.util.Chain;

public class InstrumentationUtil {

    // Helper method to create temporary local variables
    public static Local addTempLocal(Body body, Type type) {
        String name = "tmp" + System.nanoTime();
        Local local = Jimple.v().newLocal(name, type);
        body.getLocals().add(local);
        return local;
    }

    public static void insertRuntimeLog(Local local, Chain<Unit> units, Unit anchor, Body body, SootMethod logMethod) {
        SootMethod toStringMethod = Scene.v().getMethod(
                "<java.lang.String: java.lang.String valueOf(java.lang.Object)>");

        if (local.getType() instanceof IntType) {
            toStringMethod = Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(int)>");
        } else if (local.getType() instanceof BooleanType) {
            toStringMethod = Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(boolean)>");
        }

        // Convert local to string: String localStr = String.valueOf(local)
        Local valueStr = addTempLocal(body, RefType.v("java.lang.String"));
        StaticInvokeExpr toStringExpr = Jimple.v().newStaticInvokeExpr(toStringMethod.makeRef(), local);
        units.insertBefore(Jimple.v().newAssignStmt(valueStr, toStringExpr), anchor);

        SootMethod concatMethod = Scene.v().getMethod("<java.lang.String: java.lang.String concat(java.lang.String)>");
        Local label = addTempLocal(body, RefType.v("java.lang.String"));
        units.insertBefore(Jimple.v().newAssignStmt(label, StringConstant.v(local.getName() + " = ")), anchor);

        Local message = addTempLocal(body, RefType.v("java.lang.String"));
        units.insertBefore(Jimple.v().newAssignStmt(
                message,
                Jimple.v().newVirtualInvokeExpr(label, concatMethod.makeRef(), valueStr)
        ), anchor);

        // Logger.log(msg)
        InvokeExpr logCall = Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), message);
        units.insertBefore(Jimple.v().newInvokeStmt(logCall), anchor);
    }
}
