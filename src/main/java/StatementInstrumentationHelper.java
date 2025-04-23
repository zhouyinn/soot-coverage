import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.List;

public class StatementInstrumentationHelper {

    public static Value instrumentCondition(Value cond, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        String logMessage = cond.toString();  // Convert everything to string directly

        // Create the logging statement
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v("Condition: " + logMessage))
        ), anchor);

        // Return the condition (no need to do anything else for logging)
        return cond;
    }
}