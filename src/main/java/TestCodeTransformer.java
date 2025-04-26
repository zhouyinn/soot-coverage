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

        // Only instrument test classes or methods
        if (!(className.endsWith("Test") || methodName.startsWith("test"))) {
            return;
        }

        System.out.println("Injecting Logger.log() into " + method.getSignature());

        PatchingChain<Unit> units = body.getUnits();
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");

        String startMessage = "=== START TEST: " + method.getSignature() + " ===";
        String endMessage = "=== END TEST: " + method.getSignature() + " ===";

        // Inject START log at the beginning
        Unit first = units.getFirst();
        Unit startLog = Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(startMessage))
        );
        units.insertBefore(startLog, first);

        // Inject END log before return or return-void statements
        new ArrayList<>(units).stream()
                .filter(u -> u instanceof ReturnStmt || u instanceof ReturnVoidStmt)
                .forEach(u -> {
                    Unit endLog = Jimple.v().newInvokeStmt(
                            Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(endMessage))
                    );
                    units.insertBefore(endLog, u);
                });
    }
}