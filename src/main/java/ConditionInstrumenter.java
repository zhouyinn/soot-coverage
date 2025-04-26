import soot.*;
import soot.jimple.*;
import soot.util.Chain;

public class ConditionInstrumenter {
    public static Value instrument(Value cond, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        if (cond instanceof BinopExpr) {
            return handleBinaryExpr((BinopExpr) cond, anchor, units, body, logMethod);
        }

        if (cond instanceof UnopExpr) {
            return handleUnopExpr((UnopExpr) cond, anchor, units, body, logMethod);
        }

        throw new RuntimeException("Unsupported condition type: " + cond.getClass());
    }

    private static Value handleBinaryExpr(BinopExpr binop, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        // 1. create temp locals
        Local left = InstrumentationUtil.createTempForValue(binop.getOp1(), "left", units, anchor, body, logMethod);

        // 3. create temp for right operand
        Local right = InstrumentationUtil.createTempForValue(binop.getOp2(), "right", units, anchor, body, logMethod);

        // 4. log the full condition evaluation
        insertConditionLog(left, right, binop.getSymbol(), units, anchor, logMethod, body);

        // 5. rebuild the condition using the temps
        if (binop instanceof LtExpr) return Jimple.v().newLtExpr(left, right);
        if (binop instanceof LeExpr) return Jimple.v().newLeExpr(left, right);
        if (binop instanceof GtExpr) return Jimple.v().newGtExpr(left, right);
        if (binop instanceof GeExpr) return Jimple.v().newGeExpr(left, right);
        if (binop instanceof EqExpr) return Jimple.v().newEqExpr(left, right);
        if (binop instanceof NeExpr) return Jimple.v().newNeExpr(left, right);

        if (binop instanceof AddExpr) return Jimple.v().newAddExpr(left, right);
        if (binop instanceof SubExpr) return Jimple.v().newSubExpr(left, right);
        if (binop instanceof MulExpr) return Jimple.v().newMulExpr(left, right);
        if (binop instanceof DivExpr) return Jimple.v().newDivExpr(left, right);
        if (binop instanceof RemExpr) return Jimple.v().newRemExpr(left, right);
        if (binop instanceof AndExpr) return Jimple.v().newAndExpr(left, right);
        if (binop instanceof OrExpr) return Jimple.v().newOrExpr(left, right);
        if (binop instanceof XorExpr) return Jimple.v().newXorExpr(left, right);
        if (binop instanceof ShlExpr) return Jimple.v().newShlExpr(left, right);
        if (binop instanceof ShrExpr) return Jimple.v().newShrExpr(left, right);
        if (binop instanceof UshrExpr) return Jimple.v().newUshrExpr(left, right);

        throw new RuntimeException("Unsupported BinopExpr: " + binop.getClass());
    }

    private static Value handleUnopExpr(UnopExpr unop,
                                        Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        Value operand = instrument(unop.getOp(), anchor, units, body, logMethod);

        if (unop instanceof NegExpr) {
            return Jimple.v().newNegExpr(operand);
        } else {
            // Assume logical negation like !x
            return Jimple.v().newEqExpr(operand, IntConstant.v(0));
        }
    }

    private static void insertConditionLog(Local left, Local right, String op,
                                     Chain<Unit> units, Unit anchor,
                                     SootMethod logMethod, Body body) {
        if (!InstrumentationUtil.DEBUG_MODE) return;
        if (left == null || right == null) return;
        String className = body.getMethod().getDeclaringClass().getName().replace('.', '/');
        int line = anchor.getJavaSourceStartLineNumber();

        String msg = String.format(
                "{\"event\":\"CONDITION\",\"file\":\"%s.java\",\"line\":\"%d\",\"OP_left\":\"%s\",\"operator\":\"%s\",\"OP_right\":\"%s\"}",
                className, line, left.getName(), op, right.getName()
        );

        units.insertBefore(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(msg))
                ),
                anchor
        );
    }

    public static void insertSubconditionCheckedLog(int index, Unit anchor, Chain<Unit> units,
                                                    Body body, SootMethod logMethod) {
        String sourceFile = body.getMethod().getDeclaringClass().getShortName() + ".java";
        int line = anchor.getJavaSourceStartLineNumber();

        String msg = String.format(
                "{\"event\":\"SUBCONDITION_CHECKED\",\"file\":\"%s\",\"line\":\"%d\",\"index\":%d}",
                sourceFile, line, index
        );

        units.insertBefore(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(msg))
                ),
                anchor
        );
    }
}