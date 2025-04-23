import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.*;

public class TestCodeTransformer extends BodyTransformer {

    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        SootMethod method = body.getMethod();
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();

        if (!(className.endsWith("Test") || methodName.startsWith("test"))) {
            return;
        }

        System.out.println("Injecting Logger.log() and Logger.flush() into " + method.getSignature());

        PatchingChain<Unit> units = body.getUnits();
        Unit first = units.getFirst();

        // Inject Logger.log("=== START TEST: ... ===") at the beginning
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");
        String startMessage = "=== START TEST: " + method.getSignature() + " ===";
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(startMessage))
        ), first);

        // Inject Logger.flush() before every return
        SootMethod flushMethod = Scene.v().getMethod("<Logger: void flush()>");
        List<Unit> rets = new ArrayList<>();
        for (Unit u : units) {
            if (u instanceof ReturnStmt || u instanceof ReturnVoidStmt) {
                rets.add(u);
            }
        }

        for (Unit ret : rets) {
            units.insertBefore(
                    Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(flushMethod.makeRef())),
                    ret
            );
        }

        // Also add flush and return if method ends without return (fallthrough)
        Unit last = units.getLast();
        if (!(last instanceof ReturnStmt || last instanceof ReturnVoidStmt)) {
            units.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(flushMethod.makeRef())));
            units.add(Jimple.v().newReturnVoidStmt());
        }
    }
}