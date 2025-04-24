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

        // Only instrument test classes or methods
        if (!(className.endsWith("Test") || methodName.startsWith("test"))) {
            return;
        }

        System.out.println("Injecting Logger.log() into " + method.getSignature());

        PatchingChain<Unit> units = body.getUnits();
        Unit first = units.getFirst();

        // Inject Logger.log("=== START TEST: ... ===") at the beginning
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");
        String startMessage = "=== START TEST: " + method.getSignature() + " ===";

        units.insertBefore(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(startMessage))
                ),
                first
        );
    }
}