import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.Map;

public class TestCodeTransformer extends BodyTransformer {

    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        SootMethod method = body.getMethod();
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();

        if (methodName.equals("<init>")) {
            return;
        }
        // Only instrument test classes or methods
        if (!(className.endsWith("Test") || methodName.startsWith("test"))) {
            return;
        }


        PatchingChain<Unit> units = body.getUnits();
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");
        SootMethod resetMethod = Scene.v().getMethod("<Logger: void resetForNewTestCase()>");
        SootMethod flushMethod = Scene.v().getMethod("<Logger: void flushLogs()>");

        String startMessage = "=== START TEST: " + method.getSignature() + " ===";
        String endMessage = "=== END TEST: " + method.getSignature() + " ===";

        // Inject resetForNewTestCase() and START message at the beginning
        Unit first = units.getFirst();
        units.insertBefore(
                Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(resetMethod.makeRef())),
                first
        );
        units.insertBefore(
                Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(startMessage))),
                first
        );


        // Inject flushLogs() and END message before every return
        new ArrayList<>(units).stream()
                .filter(u -> u instanceof ReturnStmt || u instanceof ReturnVoidStmt)
                .forEach(u -> {
                    units.insertBefore(
                            Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(endMessage))),
                            u
                    );
                    units.insertBefore(
                            Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(flushMethod.makeRef())),
                            u
                    );
                });
    }
}