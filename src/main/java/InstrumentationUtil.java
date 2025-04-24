import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.util.Chain;

import java.util.Map;
import java.util.function.IntSupplier;

public class InstrumentationUtil {

    static void insertRuntimeLog(Value val, Chain<Unit> units, Unit anchor,
                                         Body body, SootMethod logMethod,
                                         Map<Value, Local> tempCache, IntSupplier tempCounter) {
        if (!(val instanceof Local)) return;

        Local local = (Local) val;

        // Determine correct String.valueOf overload
        SootMethod toStringMethod;
        if (local.getType() instanceof IntType) {
            toStringMethod = Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(int)>");
        } else if (local.getType() instanceof BooleanType) {
            toStringMethod = Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(boolean)>");
        } else {
            toStringMethod = Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(java.lang.Object)>");
        }

        // Convert value to string
        Local strVal = Jimple.v().newLocal("str" + local.getName(), RefType.v("java.lang.String"));
        body.getLocals().add(strVal);
        units.insertBefore(Jimple.v().newAssignStmt(strVal,
                Jimple.v().newStaticInvokeExpr(toStringMethod.makeRef(), local)), anchor);

        // Build log message: varX = <value>
        SootMethod concat = Scene.v().getMethod("<java.lang.String: java.lang.String concat(java.lang.String)>");
        Local prefix = Jimple.v().newLocal("label" + local.getName(), RefType.v("java.lang.String"));
        body.getLocals().add(prefix);
        units.insertBefore(Jimple.v().newAssignStmt(prefix,
                StringConstant.v(local.getName() + " = ")), anchor);

        Local finalMsg = Jimple.v().newLocal("msg" + local.getName(), RefType.v("java.lang.String"));
        body.getLocals().add(finalMsg);
        units.insertBefore(Jimple.v().newAssignStmt(finalMsg,
                Jimple.v().newVirtualInvokeExpr(prefix, concat.makeRef(), strVal)), anchor);

        // Final log: Logger.log(...)
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), finalMsg)), anchor);
    }
}
