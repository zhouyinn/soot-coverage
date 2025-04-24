import soot.*;
import soot.jimple.*;
import soot.util.Chain;


public class ConditionInstrumenter {

    public static Value instrument(Value cond, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        if (cond instanceof InvokeExpr) {
            return handleInvokeExpr((InvokeExpr) cond, anchor, units, body, logMethod);
        }

        if (cond instanceof Local || cond instanceof Constant) {
            return handleLocalOrConstant(cond, anchor, units, body, logMethod);
        }

        if (cond instanceof BinopExpr) {
            return handleBinaryExpr((BinopExpr) cond, anchor, units, body, logMethod);
        }

        if (cond instanceof UnopExpr) {
            return handleUnaryExpr((UnopExpr) cond, anchor, units, body, logMethod);
        }

        throw new RuntimeException("Unsupported condition type: " + cond.getClass());
    }

    private static Value handleInvokeExpr(InvokeExpr invoke, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        Local result = InstrumentationUtil.addTempLocal(body, invoke.getType());
        units.insertBefore(Jimple.v().newAssignStmt(result, invoke), anchor);

        String logText = result.getName() + " = " + invoke;
        units.insertBefore(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(logText))
        ), anchor);

        InstrumentationUtil.insertRuntimeLog(result, units, anchor, body, logMethod);
        return result;
    }

    private static Value handleLocalOrConstant(Value cond, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        Local temp = InstrumentationUtil.addTempLocal(body, cond.getType());
        units.insertBefore(Jimple.v().newAssignStmt(temp, cond), anchor);

        if (cond instanceof Local) {
            logLocalLinkedAssign((Local) cond, temp, units, anchor, logMethod);
        }

        InstrumentationUtil.insertRuntimeLog(temp, units, anchor, body, logMethod);
        return temp;
    }

    private static void logLocalLinkedAssign(Local local, Local temp, Chain<Unit> units, Unit anchor, SootMethod logMethod) {
        for (Unit u : units) {
            if (u instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) u;
                if (assign.getLeftOp().equals(local)) {
                    String inferred = temp.getName() + " = " + assign.getRightOp();
                    units.insertBefore(Jimple.v().newInvokeStmt(
                            Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(inferred))
                    ), anchor);
                    break;
                }
            }
        }
    }

    private static Value handleBinaryExpr(BinopExpr binop, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        Value left = binop.getOp1();
        Value right = binop.getOp2();
        Local leftTemp = InstrumentationUtil.addTempLocal(body, left.getType());
        units.insertBefore(Jimple.v().newAssignStmt(leftTemp, left), anchor);
        InstrumentationUtil.insertRuntimeLog(leftTemp, units, anchor, body, logMethod);
        left = leftTemp;

        Local rightTemp = InstrumentationUtil.addTempLocal(body, right.getType());
        units.insertBefore(Jimple.v().newAssignStmt(rightTemp, right), anchor);
        InstrumentationUtil.insertRuntimeLog(rightTemp, units, anchor, body, logMethod);
        right = rightTemp;

        logCondition(left, right, binop.getSymbol(), units, anchor, logMethod);

        if (binop instanceof LtExpr) return Jimple.v().newLtExpr(left, right);
        if (binop instanceof LeExpr) return Jimple.v().newLeExpr(left, right);
        if (binop instanceof GtExpr) return Jimple.v().newGtExpr(left, right);
        if (binop instanceof GeExpr) return Jimple.v().newGeExpr(left, right);
        if (binop instanceof EqExpr) return Jimple.v().newEqExpr(left, right);
        if (binop instanceof NeExpr) return Jimple.v().newNeExpr(left, right);

        throw new RuntimeException("Unsupported BinopExpr type: " + binop.getClass());
    }

    private static Value handleUnaryExpr(UnopExpr unop, Unit anchor, Chain<Unit> units, Body body, SootMethod logMethod) {
        Value operand = instrument(unop.getOp(), anchor, units, body, logMethod);
        Value rebuilt;

        if (unop instanceof NegExpr) {
            rebuilt = Jimple.v().newNegExpr(operand);
        } else {
            rebuilt = Jimple.v().newEqExpr(operand, IntConstant.v(0));
        }

        Local temp = InstrumentationUtil.addTempLocal(body, rebuilt.getType());
        units.insertBefore(Jimple.v().newAssignStmt(temp, rebuilt), anchor);
        InstrumentationUtil.insertRuntimeLog(temp, units, anchor, body, logMethod);
        return temp;
    }


    private static void logCondition(Value left, Value right, String op, Chain<Unit> units, Unit anchor, SootMethod logMethod) {
        String msg = "Condition: " + left + " " + op + " " + right;
        units.insertBefore(
                Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(logMethod.makeRef(), StringConstant.v(msg))),
                anchor
        );
    }
}