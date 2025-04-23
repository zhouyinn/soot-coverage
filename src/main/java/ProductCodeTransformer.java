import soot.*;
import soot.jimple.*;
import java.util.Set;
import java.util.Iterator;
import java.util.Map;

public class ProductCodeTransformer extends BodyTransformer {
    private final Map<String, Set<Integer>> linesToInstrument;

    public ProductCodeTransformer(Map<String, Set<Integer>> linesToInstrument) {
        this.linesToInstrument = linesToInstrument;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        String className = body.getMethod().getDeclaringClass().getName();
        if (className.startsWith("Logger") || className.endsWith("Test") || body.getMethod().getName().equals("<clinit>")) return;
        System.out.println("Instrumenting: " + body.getMethod().getSignature());

        PatchingChain<Unit> units = body.getUnits();
        SootMethod logMethod = Scene.v().getMethod("<Logger: void log(java.lang.String)>");

        String classFilePath = className.replace('.', '/') + ".java";  // Convert class name to file path

        // Loop through all the lines that need to be instrumented
        Set<Integer> linesInFile = linesToInstrument.entrySet().stream()
                .filter(entry -> classFilePath.contains(entry.getKey()) || entry.getKey().contains(classFilePath))  // Check both ways
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (linesInFile == null) return; // If no lines found to instrument, skip

        // Iterate over the units (statements) in the body
        for (Iterator<Unit> it = units.snapshotIterator(); it.hasNext();) {
            Stmt stmt = (Stmt) it.next();
            int line = stmt.getJavaSourceStartLineNumber();

            // Only instrument if the current line is in the set of lines to instrument
            if (line <= 0 || !linesInFile.contains(line)) continue;

            String extra = stmt.getClass().getSimpleName() + " " + stmt;
            String id = body.getMethod().getSignature() + ":" + line + " → " + extra;
            InvokeExpr logCall = Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(id));
            units.insertBefore(Jimple.v().newInvokeStmt(logCall), stmt);

            // If the statement is a conditional, instrument the condition
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