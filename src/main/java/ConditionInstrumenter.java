import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class ConditionInstrumenter {
    private static final AtomicInteger counter = new AtomicInteger(1);

    public static Value instrument(Value cond, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        if (cond instanceof InvokeExpr || cond instanceof Local || cond instanceof Constant) {
            return getOrCreateTemp(cond, anchor, units, body);
        }

        if (cond instanceof BinopExpr) {
            return handleBinaryExpr((BinopExpr) cond, anchor, units, logMethod, body);
        }

        if (cond instanceof UnopExpr) {
            Value operand = instrument(((UnopExpr) cond).getOp(), anchor, units, body, logMethod);
            if (cond instanceof NegExpr) {
                return Jimple.v().newNegExpr(operand);
            } else {
                return Jimple.v().newEqExpr(operand, IntConstant.v(0));
            }
        }

        throw new RuntimeException("Unsupported condition type: " + cond.getClass());
    }

    private static Value handleBinaryExpr(BinopExpr binop, Unit anchor, Chain<Unit> units, SootMethod logMethod, Body body) {
        Value left = getOrCreateTemp(binop.getOp1(), anchor, units, body);
        Value right = getOrCreateTemp(binop.getOp2(), anchor, units, body);

        logCondition(left, right, binop.getSymbol(), units, anchor, logMethod, body);

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

    private static Local getOrCreateTemp(Value expr, Unit anchor, Chain<Unit> units, Body body) {

        Local temp = Jimple.v().newLocal("__autogen" + counter.getAndIncrement(), expr.getType());
        body.getLocals().add(temp);
        units.insertBefore(Jimple.v().newAssignStmt(temp, expr), anchor);
        return temp;
    }

    private static void logCondition(Value left, Value right, String op,
                                     Chain<Unit> units, Unit anchor,
                                     SootMethod logMethod, Body body) {

        // Ensure values are logged
        InstrumentationUtil.insertRuntimeLog(left, units, anchor, body, logMethod);
        InstrumentationUtil.insertRuntimeLog(right, units, anchor, body, logMethod);

        // Then log the comparison
        String msg = "Condition: " + left + " " + op + " " + right;
        units.insertBefore(
                Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(msg))),
                anchor
        );
    }
}